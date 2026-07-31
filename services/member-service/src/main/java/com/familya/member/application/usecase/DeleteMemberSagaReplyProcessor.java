package com.familya.member.application.usecase;

import com.familya.member.adapter.in.kafka.DeleteMemberSagaDeadLetterStore;
import com.familya.member.application.port.in.DeleteMemberSagaReplyCommand;
import com.familya.member.application.port.out.DeleteMemberSagaGateway;
import com.familya.member.application.port.out.DeleteMemberSagaRepository;
import com.familya.member.domain.model.DeleteMemberSagaState;
import com.familya.member.domain.model.DeleteMemberSagaStep;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class DeleteMemberSagaReplyProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteMemberSagaReplyProcessor.class);

    private final DeleteMemberSagaRepository sagaRepo;
    private final DeleteMemberSagaGateway gateway;
    private final DeleteMemberSagaDeadLetterStore deadLetterStore;
    private final PlatformMetrics metrics;
    private final Clock clock;

    public DeleteMemberSagaReplyProcessor(DeleteMemberSagaRepository sagaRepo,
                                          DeleteMemberSagaGateway gateway,
                                          DeleteMemberSagaDeadLetterStore deadLetterStore,
                                          PlatformMetrics metrics,
                                          Clock clock) {
        this.sagaRepo = sagaRepo;
        this.gateway = gateway;
        this.deadLetterStore = deadLetterStore;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public void process(DeleteMemberSagaReplyCommand cmd) {
        Optional<DeleteMemberSagaState> opt = sagaRepo.findState(cmd.operationId());
        if (opt.isEmpty()) {
            LOG.warn("Ignoring reply for unknown operationId={}", cmd.operationId());
            return;
        }
        DeleteMemberSagaState state = opt.get();
        if (state.state().isTerminal()) {
            LOG.info("Ignoring reply for terminal Saga operationId={} state={}", state.operationId(), state.state());
            return;
        }

        List<DeleteMemberSagaStep> steps = sagaRepo.listSteps(cmd.operationId());
        DeleteMemberSagaStep current = steps.stream()
                .filter(s -> s.stepCode().equals(cmd.stepCode()))
                .filter(s -> s.participantService().equals(cmd.participantService()))
                .findFirst()
                .orElse(null);
        if (current == null) {
            LOG.warn("Ignoring reply op={} step={} participant={} — no matching step",
                    cmd.operationId(), cmd.stepCode(), cmd.participantService());
            return;
        }

        Instant now = clock.now();
        metrics.mutationAccepted("member-service", "deleteMember.reply");

        if (cmd.compensationApplied()) {
            if (cmd.failed()) {
                handleCompensationFailure(state, current, cmd.failureCode(), cmd.failureMessage(), now);
            } else {
                handleCompensationReply(state, steps, current, now);
            }
            return;
        }
        if (state.state() == DeleteMemberSagaState.State.COMPENSATING) {
            LOG.info("Ignoring forward reply while compensating operationId={} step={}",
                    state.operationId(), current.stepCode());
            return;
        }
        if (current.state() != DeleteMemberSagaStep.State.DISPATCHED) {
            LOG.info("Ignoring stale reply operationId={} step={} state={}",
                    state.operationId(), current.stepCode(), current.state());
            return;
        }

        if (cmd.failed()) {
            handleParticipantFailure(state, steps, current, cmd.failureCode(), cmd.failureMessage(), now);
            return;
        }

        if (!satisfiesBarrier(state, cmd)) {
            handleBarrierFailure(state, current, now);
            return;
        }

        current.ack(now, cmd.appliedAggregateVersion(), cmd.appliedEpoch());
        sagaRepo.updateStep(current);
        if (current.sequenceNo() >= 5) {
            state.markIrreversible(now);
        }

        DeleteMemberSagaStep next = nextPendingStep(steps, current.sequenceNo());
        if (next == null) {
            state.transitionTo(DeleteMemberSagaState.State.SUCCEEDED, now);
            sagaRepo.saveState(state);
            gateway.stageOperationStateChanged(state);
            return;
        }

        dispatchStep(state, next, now);
        sagaRepo.saveState(state);
        gateway.stageOperationStateChanged(state);
    }

    private void handleCompensationReply(DeleteMemberSagaState state,
                                         List<DeleteMemberSagaStep> steps,
                                         DeleteMemberSagaStep current,
                                         Instant now) {
        if (state.state() != DeleteMemberSagaState.State.COMPENSATING
                || current.state() != DeleteMemberSagaStep.State.DISPATCHED) {
            return;
        }
        current.compensate(now);
        sagaRepo.updateStep(current);
        boolean complete = sagaRepo.listSteps(state.operationId()).stream()
                .filter(DeleteMemberSagaStep::compensatable)
                .noneMatch(s -> s.state() == DeleteMemberSagaStep.State.ACK
                        || s.state() == DeleteMemberSagaStep.State.DISPATCHED);
        if (complete) {
            state.transitionTo(DeleteMemberSagaState.State.FAILED, now);
            sagaRepo.saveState(state);
            gateway.stageOperationStateChanged(state);
        }
    }

    private void handleCompensationFailure(DeleteMemberSagaState state,
                                           DeleteMemberSagaStep current,
                                           String code, String message,
                                           Instant now) {
        if (state.state() != DeleteMemberSagaState.State.COMPENSATING
                || current.state() != DeleteMemberSagaStep.State.DISPATCHED) return;
        if (current.attemptCount() < current.maxAttempts()) {
            UUID token = UUID.randomUUID();
            Instant stepDeadline = now.plusSeconds(30);
            if (sagaRepo.tryClaimCompensation(current.operationId(), current.sequenceNo(),
                    token, now, stepDeadline)) {
                current.markCompensationDispatched(now, token, stepDeadline);
                sagaRepo.updateStep(current);
                gateway.stageCompensation(state, current);
            }
            return;
        }
        current.markDeadLettered(code, message, now);
        sagaRepo.updateStep(current);
        state.recordFailure(code, message, now);
        state.transitionTo(DeleteMemberSagaState.State.MANUAL_REVIEW, now);
        sagaRepo.saveState(state);
        deadLetterStore.saveRetryExhausted(
                state.operationId(), current.participantService(), current.stepCode(),
                current.attemptCount(), new IllegalStateException("Compensation failed: " + code + " " + message));
        gateway.stageOperationStateChanged(state);
    }

    private void handleParticipantFailure(DeleteMemberSagaState state,
                                          List<DeleteMemberSagaStep> steps,
                                          DeleteMemberSagaStep current,
                                          String code, String message, Instant now) {
        current.fail(code, message, now);
        sagaRepo.updateStep(current);
        state.recordFailure(code, message, now);

        if (current.attemptCount() < current.maxAttempts()) {
            dispatchStep(state, current, now);
            sagaRepo.saveState(state);
            gateway.stageOperationStateChanged(state);
            return;
        }

        if (!passedIrreversibleBoundary(state, current)) {
            deadLetterStore.saveRetryExhausted(
                    state.operationId(), current.participantService(), current.stepCode(),
                    current.attemptCount(),
                    new IllegalStateException("Participant retry exhausted: " + code + " " + message));
            boolean stateChanged = state.state() != DeleteMemberSagaState.State.COMPENSATING;
            if (stateChanged) {
                state.transitionTo(DeleteMemberSagaState.State.COMPENSATING, now);
            }
            sagaRepo.saveState(state);
            if (stateChanged) {
                gateway.stageOperationStateChanged(state);
                compensatePreviousSteps(state, steps, current);
            }
            return;
        }

        current.markDeadLettered(code, message, now);
        sagaRepo.updateStep(current);
        state.transitionTo(DeleteMemberSagaState.State.MANUAL_REVIEW, now);
        sagaRepo.saveState(state);
        deadLetterStore.saveRetryExhausted(
                state.operationId(), current.participantService(), current.stepCode(),
                current.attemptCount(),
                new IllegalStateException("Participant retry exhausted: " + code + " " + message));
        gateway.stageOperationStateChanged(state);
    }

    private void handleBarrierFailure(DeleteMemberSagaState state,
                                      DeleteMemberSagaStep current, Instant now) {
        String code = "BARRIER_NOT_MET";
        String message = "step " + current.stepCode() + " did not meet barrier";
        current.fail(code, message, now);
        sagaRepo.updateStep(current);
        state.recordFailure(code, message, now);
        state.transitionTo(DeleteMemberSagaState.State.MANUAL_REVIEW, now);
        sagaRepo.saveState(state);
        gateway.stageOperationStateChanged(state);
    }

    private void dispatchStep(DeleteMemberSagaState state, DeleteMemberSagaStep step, Instant now) {
        if (step.attemptCount() > 0 && step.dispatchToken() != null && step.state() == DeleteMemberSagaStep.State.DISPATCHED) {
            sagaRepo.updateStep(step);
            gateway.stageFirstStep(state, step);
            return;
        }
        UUID token = UUID.randomUUID();
        Instant stepDeadline = now.plusSeconds(30);
        boolean claimed = sagaRepo.tryClaimDispatch(step.operationId(), step.sequenceNo(), token, now, stepDeadline);
        if (!claimed) {
            LOG.warn("dispatchStep claim conflict op={} seq={} step.state={}; skipping publish to avoid double dispatch",
                    step.operationId(), step.sequenceNo(), step.state());
            return;
        }
        step.claimDispatch(token, now, stepDeadline);
        sagaRepo.updateStep(step);
        gateway.stageFirstStep(state, step);
    }

    private static boolean passedIrreversibleBoundary(DeleteMemberSagaState state, DeleteMemberSagaStep step) {
        return state.irreversibleAt() != null || step.sequenceNo() >= 5;
    }

    private boolean satisfiesBarrier(DeleteMemberSagaState state, DeleteMemberSagaReplyCommand cmd) {
        return cmd.appliedAggregateVersion() >= state.targetAggregateVersion()
                && cmd.appliedEpoch() >= state.targetEpoch();
    }

    private static DeleteMemberSagaStep nextPendingStep(List<DeleteMemberSagaStep> steps, int currentSeq) {
        return steps.stream()
                .filter(s -> s.sequenceNo() > currentSeq)
                .filter(s -> s.state() == DeleteMemberSagaStep.State.PENDING)
                .findFirst()
                .orElse(null);
    }

    private void compensatePreviousSteps(DeleteMemberSagaState state,
                                         List<DeleteMemberSagaStep> steps,
                                         DeleteMemberSagaStep failedStep) {
        Instant now = clock.now();
        for (int i = failedStep.sequenceNo() - 1; i >= 1; i--) {
            int seq = i;
            steps.stream().filter(s -> s.sequenceNo() == seq).findFirst()
                    .ifPresent(prev -> {
                        if (prev.compensatable()
                                && prev.state() == DeleteMemberSagaStep.State.ACK
                                && !passedIrreversibleBoundary(state, prev)) {
                            UUID token = UUID.randomUUID();
                            Instant stepDeadline = now.plusSeconds(30);
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

    public interface Clock { Instant now(); }
}