package com.familya.treeaccess.application.saga.scheduling;

import com.familya.treeaccess.adapter.in.kafka.DeleteTreeSagaDeadLetterStore;
import com.familya.treeaccess.application.port.out.DeleteTreeSagaGateway;
import com.familya.treeaccess.application.port.out.DeleteTreeSagaRepository;
import com.familya.treeaccess.application.saga.config.SagaDeadlineProperties;
import com.familya.treeaccess.application.saga.config.SagaProperties;
import com.familya.treeaccess.application.saga.retry.SagaClock;
import com.familya.treeaccess.application.saga.retry.SagaRetryPolicy;
import com.familya.treeaccess.domain.model.DeleteTreeSagaState;
import com.familya.treeaccess.domain.model.DeleteTreeSagaStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "familya.treeauth.saga.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class DeleteTreeSagaDeadlineScanner {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteTreeSagaDeadlineScanner.class);

    static final String FAILURE_STEP_TIMEOUT = "STEP_TIMEOUT";
    static final String FAILURE_OPERATION_DEADLINE_EXCEEDED = "OPERATION_DEADLINE_EXCEEDED";
    static final String FAILURE_RETRY_EXHAUSTED = "RETRY_EXHAUSTED";
    static final String FAILURE_COMPENSATION_RETRY_EXHAUSTED = "COMPENSATION_RETRY_EXHAUSTED";
    static final String FAILURE_DISPATCH_CLAIM_CONFLICT = "DISPATCH_CLAIM_CONFLICT";

    static final String FAILURE_ROUTING_MANUAL_REVIEW = "MANUAL_REVIEW";
    static final String FAILURE_ROUTING_COMPENSATING = "COMPENSATING";

    static final String OWNER_SERVICE = "tree-access-service";

    private final DeleteTreeSagaRepository sagaRepo;
    private final DeleteTreeSagaGateway gateway;
    private final DeleteTreeSagaDeadLetterStore deadLetterStore;
    private final SagaClock clock;
    private final SagaRetryPolicy retryPolicy;
    private final SagaDeadlineProperties deadline;

    public DeleteTreeSagaDeadlineScanner(DeleteTreeSagaRepository sagaRepo,
                                         DeleteTreeSagaGateway gateway,
                                         DeleteTreeSagaDeadLetterStore deadLetterStore,
                                         SagaClock clock,
                                         SagaRetryPolicy retryPolicy,
                                         SagaProperties props) {
        this.sagaRepo = sagaRepo;
        this.gateway = gateway;
        this.deadLetterStore = deadLetterStore;
        this.clock = clock;
        this.retryPolicy = retryPolicy;
        this.deadline = props.getDeadline();
    }

    @Scheduled(fixedDelayString = "${familya.treeauth.saga.deadline.scan-interval-ms:60000}")
    public void scan() {
        Instant now = clock.now();
        int batch = deadline.getBatchSize();
        for (DeleteTreeSagaState op : listExpiredOperations(now, batch)) {
            try { processOperation(op, now); }
            catch (RuntimeException e) { LOG.warn("DeadlineScanner processOperation failed op={} err={}", op.operationId(), e.toString()); }
        }
        for (DeleteTreeSagaStep step : sagaRepo.listTimedOutSteps(now, batch)) {
            try { processStepTimeout(step, now); }
            catch (RuntimeException e) { LOG.warn("DeadlineScanner processStepTimeout failed op={} seq={} err={}", step.operationId(), step.sequenceNo(), e.toString()); }
        }
        for (DeleteTreeSagaStep step : sagaRepo.listRetryableSteps(now, batch)) {
            try { processRetry(step, now); }
            catch (RuntimeException e) { LOG.warn("DeadlineScanner processRetry failed op={} seq={} err={}", step.operationId(), step.sequenceNo(), e.toString()); }
        }
    }

    private List<DeleteTreeSagaState> listExpiredOperations(Instant now, int batch) {
        return sagaRepo.listActivePastDeadline().stream().limit(batch).toList();
    }

    @Transactional
    public void processOperation(DeleteTreeSagaState op, Instant now) {
        var current = sagaRepo.findState(op.operationId()).orElse(null);
        if (current == null || current.state().isTerminal()) return;
        if (current.state() == DeleteTreeSagaState.State.COMPENSATING) return;
        var steps = sagaRepo.listSteps(current.operationId());
        boolean canCompensate = hasCompensatableAckedStep(current, steps);
        if (canCompensate && !passedIrreversibleBoundary(current)) {
            current.recordFailure(FAILURE_OPERATION_DEADLINE_EXCEEDED,
                    "Operation deadline exceeded; compensating", now);
            if (current.state() != DeleteTreeSagaState.State.COMPENSATING) {
                current.transitionTo(DeleteTreeSagaState.State.COMPENSATING, now);
            }
            sagaRepo.saveState(current);
            gateway.stageOperationStateChanged(current, FAILURE_ROUTING_COMPENSATING);
            compensatePreviousSteps(current, steps, findLatestAckedStep(steps));
            return;
        }
        sagaRepo.markOperationManualReview(current.operationId(),
                FAILURE_OPERATION_DEADLINE_EXCEEDED,
                "Operation deadline exceeded without rollback path", now);
        var refreshed = sagaRepo.findState(current.operationId()).orElse(current);
        gateway.stageOperationStateChanged(refreshed, FAILURE_ROUTING_MANUAL_REVIEW);
    }

    @Transactional
    public void processStepTimeout(DeleteTreeSagaStep stepRow, Instant now) {
        var step = sagaRepo.listSteps(stepRow.operationId()).stream()
                .filter(s -> s.sequenceNo() == stepRow.sequenceNo())
                .findFirst().orElse(null);
        if (step == null || step.state() != DeleteTreeSagaStep.State.DISPATCHED) return;
        var state = sagaRepo.findState(step.operationId()).orElse(null);
        if (state == null || state.state().isTerminal()) return;

        if (step.attemptCount() < step.maxAttempts()) {
            Instant next = retryPolicy.nextAttemptAt(now, step.attemptCount() + 1, state.deadlineAt());
            boolean released = sagaRepo.releaseOrScheduleRetry(step.operationId(), step.sequenceNo(), now, next,
                    FAILURE_STEP_TIMEOUT, "Step timed out; rescheduled");
            if (!released) {
                LOG.info("releaseOrScheduleRetry no-op for op={} seq={} (step state changed)",
                        step.operationId(), step.sequenceNo());
            }
            return;
        }

        if (OWNER_SERVICE.equals(step.participantService())
                || !step.compensatable()
                || passedIrreversibleBoundary(state)) {
            escalateManualReview(step, state, now,
                    FAILURE_RETRY_EXHAUSTED, "Retry exhausted past irreversible boundary");
            return;
        }

        if (state.state() == DeleteTreeSagaState.State.COMPENSATING) {
            escalateManualReview(step, state, now,
                    FAILURE_COMPENSATION_RETRY_EXHAUSTED,
                    "Compensation retry exhausted for " + step.stepCode());
            return;
        }

        UUID token = UUID.randomUUID();
        Instant stepDeadline = now.plusMillis(deadline.getStepTimeoutMs());
        boolean claimed = sagaRepo.tryClaimCompensation(step.operationId(), step.sequenceNo(),
                token, now, stepDeadline);
        if (!claimed) {
            return;
        }
        step.dispatch(now);
        sagaRepo.updateStep(step);
        gateway.stageCompensation(state, step);
        state.recordFailure(FAILURE_RETRY_EXHAUSTED,
                "Retry exhausted for " + step.stepCode(), now);
        boolean stateChanged = state.state() != DeleteTreeSagaState.State.COMPENSATING;
        if (stateChanged) {
            state.transitionTo(DeleteTreeSagaState.State.COMPENSATING, now);
        }
        sagaRepo.saveState(state);
        if (stateChanged) {
            gateway.stageOperationStateChanged(state, FAILURE_ROUTING_COMPENSATING);
        }
        deadLetterStore.saveRetryExhausted(state.operationId(), step.participantService(),
                step.stepCode(), step.attemptCount(),
                new IllegalStateException("Retry exhausted: " + FAILURE_RETRY_EXHAUSTED));
    }

    private void escalateManualReview(DeleteTreeSagaStep step, DeleteTreeSagaState state,
                                      Instant now, String failureCode, String failureMessage) {
        step.markFailed(failureCode, failureMessage, now);
        sagaRepo.updateStep(step);
        sagaRepo.markOperationManualReview(state.operationId(), failureCode, failureMessage, now);
        var refreshed = sagaRepo.findState(state.operationId()).orElse(state);
        gateway.stageOperationStateChanged(refreshed, FAILURE_ROUTING_MANUAL_REVIEW);
        deadLetterStore.saveRetryExhausted(state.operationId(), step.participantService(),
                step.stepCode(), step.attemptCount(),
                new IllegalStateException(failureCode + ": " + failureMessage));
    }

    @Transactional
    public void processRetry(DeleteTreeSagaStep stepRow, Instant now) {
        var state = sagaRepo.findState(stepRow.operationId()).orElse(null);
        if (state == null || state.state().isTerminal()) return;
        var step = sagaRepo.listSteps(state.operationId()).stream()
                .filter(s -> s.sequenceNo() == stepRow.sequenceNo())
                .findFirst().orElse(null);
        if (step == null || step.state() != DeleteTreeSagaStep.State.FAILED) return;

        if (!now.isBefore(state.deadlineAt())) {
            sagaRepo.markOperationManualReview(state.operationId(),
                    FAILURE_OPERATION_DEADLINE_EXCEEDED,
                    "Retry would exceed operation deadline", now);
            var refreshed = sagaRepo.findState(state.operationId()).orElse(state);
            gateway.stageOperationStateChanged(refreshed, FAILURE_ROUTING_MANUAL_REVIEW);
            return;
        }

        UUID token = UUID.randomUUID();
        Instant stepDeadlineAt = now.plusMillis(deadline.getStepTimeoutMs());
        boolean claimed = sagaRepo.tryClaimDispatch(step.operationId(), step.sequenceNo(), token, now, stepDeadlineAt);
        if (!claimed) {
            Instant next = retryPolicy.nextAttemptAt(now, step.attemptCount() + 1, state.deadlineAt());
            sagaRepo.releaseOrScheduleRetry(step.operationId(), step.sequenceNo(), now, next,
                    FAILURE_DISPATCH_CLAIM_CONFLICT, "Dispatch claim conflict; rescheduled");
            return;
        }

        if (state.state() == DeleteTreeSagaState.State.COMPENSATING) {
            gateway.stageCompensation(state, step);
        } else {
            gateway.stageFirstStep(state, step);
        }
    }

    private boolean hasCompensatableAckedStep(DeleteTreeSagaState state, List<DeleteTreeSagaStep> steps) {
        boolean irreversiblePassed = passedIrreversibleBoundary(state);
        for (DeleteTreeSagaStep s : steps) {
            if (OWNER_SERVICE.equals(s.participantService())) continue;
            if (s.compensatable() && s.state() == DeleteTreeSagaStep.State.ACK
                    && !irreversiblePassed) {
                return true;
            }
        }
        return false;
    }

    private DeleteTreeSagaStep findLatestAckedStep(List<DeleteTreeSagaStep> steps) {
        return steps.stream()
                .filter(s -> s.state() == DeleteTreeSagaStep.State.ACK)
                .reduce((a, b) -> a.sequenceNo() > b.sequenceNo() ? a : b)
                .orElse(null);
    }

    private boolean passedIrreversibleBoundary(DeleteTreeSagaState state) {
        return state.irreversibleAt() != null;
    }

    private void compensatePreviousSteps(DeleteTreeSagaState state,
                                         List<DeleteTreeSagaStep> steps,
                                         DeleteTreeSagaStep boundaryStep) {
        Instant now = clock.now();
        int upper = boundaryStep == null ? steps.size() : boundaryStep.sequenceNo();
        for (int i = upper; i >= 1; i--) {
            int seq = i;
            steps.stream().filter(s -> s.sequenceNo() == seq).findFirst()
                    .ifPresent(prev -> {
                        if (!OWNER_SERVICE.equals(prev.participantService())
                                && prev.compensatable()
                                && prev.state() == DeleteTreeSagaStep.State.ACK
                                && !passedIrreversibleBoundary(state)) {
                            UUID token = UUID.randomUUID();
                            Instant stepDeadline = now.plusMillis(deadline.getStepTimeoutMs());
                            if (sagaRepo.tryClaimCompensation(prev.operationId(), prev.sequenceNo(),
                                    token, now, stepDeadline)) {
                                prev.markCompensationDispatched(now, token, stepDeadline);
                                sagaRepo.updateStep(prev);
                                gateway.stageCompensation(state, prev);
                            }
                        }
                    });
        }
    }
}
