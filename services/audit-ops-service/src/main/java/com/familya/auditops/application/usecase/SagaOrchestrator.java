package com.familya.auditops.application.usecase;

import com.familya.auditops.application.port.in.RecordParticipantReplyCommand;
import com.familya.auditops.application.port.out.AuditAppender;
import com.familya.auditops.application.port.out.OperationEventBus;
import com.familya.auditops.application.port.out.OperationRepository;
import com.familya.auditops.application.port.out.SagaCommandBus;
import com.familya.auditops.application.port.out.SagaStateRepository;
import com.familya.auditops.domain.event.OperationAdvanced;
import com.familya.auditops.domain.exception.OperationNotFoundException;
import com.familya.auditops.domain.exception.SagaConflictException;
import com.familya.auditops.domain.model.AuditEvent;
import com.familya.auditops.domain.model.Operation;
import com.familya.auditops.domain.model.OperationStatus;
import com.familya.auditops.domain.model.SagaStep;
import com.familya.auditops.domain.model.SagaTransitions;
import com.familya.auditops.domain.model.StepStatus;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Saga orchestrator. Receives participant replies (Kafka), drives
 * the operation state machine forward, and dispatches compensating
 * commands when a participant fails. The orchestrator enforces:
 *
 * <ul>
 *   <li>Optimistic concurrency on every operation and step transition.</li>
 *   <li>Target-revision completion: an operation only becomes
 *       {@link OperationStatus#SUCCEEDED} when every required step
 *       has reported a target revision/epoch &gt;= the operation's
 *       target.</li>
 *   <li>Retry policy with bounded jitter; poison messages move to
 *       the saga-dead-letter table and the operation transitions to
 *       {@link OperationStatus#MANUAL_REVIEW}.</li>
 *   <li>Compensation boundaries: only steps that have not crossed
 *       their irreversible boundary can be compensated. The
 *       orchestrator routes violations to {@code MANUAL_REVIEW}.</li>
 * </ul>
 *
 * <p>The orchestrator never participates in cross-service
 * transactions; every state change is local and emits an outbox row
 * (Task 13 / ADR-003 / ADR-007).</p>
 */
@Service
public class SagaOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(SagaOrchestrator.class);

    private final OperationRepository operationRepo;
    private final SagaStateRepository sagaRepo;
    private final SagaCommandBus commandBus;
    private final AuditAppender audit;
    private final OperationEventBus eventBus;
    private final PlatformMetrics metrics;
    private final Clock clock;
    private final int maxAttempts;
    private final long retryBaseMs;
    private final long retryMaxMs;

    public SagaOrchestrator(OperationRepository operationRepo,
                            SagaStateRepository sagaRepo,
                            SagaCommandBus commandBus,
                            AuditAppender audit,
                            OperationEventBus eventBus,
                            PlatformMetrics metrics,
                            Clock clock,
                            @Value("${familya.auditops.saga.max-attempts:5}") int maxAttempts,
                            @Value("${familya.auditops.saga.retry-base-ms:500}") long retryBaseMs,
                            @Value("${familya.auditops.saga.retry-max-ms:30000}") long retryMaxMs) {
        this.operationRepo = operationRepo;
        this.sagaRepo = sagaRepo;
        this.commandBus = commandBus;
        this.audit = audit;
        this.eventBus = eventBus;
        this.metrics = metrics;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.retryBaseMs = retryBaseMs;
        this.retryMaxMs = retryMaxMs;
    }

    /**
     * Apply a participant reply. The reply MUST arrive on a Kafka
     * topic that the consumer has already dedup'd through the inbox;
     * this method trusts the consumer and applies the reply to the
     * local state machine.
     */
    @Transactional
    public void applyReply(RecordParticipantReplyCommand reply) {
        Operation op = operationRepo.findById(reply.operationId())
                .orElseThrow(() -> new OperationNotFoundException(reply.operationId().toString()));
        Instant now = Instant.now(clock);

        if (op.status().isTerminal() && op.status() != OperationStatus.MANUAL_REVIEW) {
            LOG.debug("Ignoring reply for terminal operation={} status={}",
                    op.id(), op.status());
            return;
        }

        switch (reply.outcome()) {
            case ACKED -> handleAck(op, reply, now);
            case FAILED -> handleFailure(op, reply, now);
            case COMPENSATED -> handleCompensated(op, reply, now);
            case DEAD_LETTERED -> handleDeadLetter(op, reply, now);
        }
    }

    /**
     * Move an operation from {@code MANUAL_REVIEW} (or
     * {@code COMPENSATING}) to {@code RUNNING}, re-dispatching every
     * non-acked step. Idempotent: a step already in
     * {@link StepStatus#ACKED} is skipped.
     */
    @Transactional
    public void retry(UUID operationId, UUID operatorUserId, String reason) {
        Operation op = operationRepo.findById(operationId)
                .orElseThrow(() -> new OperationNotFoundException(operationId.toString()));
        if (!op.status().equals(OperationStatus.MANUAL_REVIEW)
                && !op.status().equals(OperationStatus.COMPENSATING)) {
            throw new SagaConflictException(
                    "Operation " + operationId + " is not eligible for retry (status=" + op.status() + ")");
        }
        Instant now = Instant.now(clock);
        SagaTransitions.requireAllowed(op.status(), OperationStatus.RUNNING);
        Operation advanced = operationRepo.transition(op.id(), op.version(),
                OperationStatus.RUNNING, null, null, now);

        for (SagaStep step : sagaRepo.listSteps(op.id())) {
            if (step.status() == StepStatus.ACKED || step.status() == StepStatus.COMPENSATED) {
                continue;
            }
            sagaRepo.transitionStep(op.id(), step.participantService(), step.stepName(),
                    StepStatus.DISPATCHED, null, null, now);
            commandBus.dispatchCommand(buildCommand(advanced, step, now, retryJitterMillis()));
        }

        audit.append(new AuditEvent(
                UUID.randomUUID(), op.id(), op.correlationId(),
                operatorUserId, AuditEvent.ActorKind.OPERATOR,
                "operation.retry",
                "operation", op.id().toString(),
                Map.of("reason", reason == null ? "" : reason),
                now, null));

        eventBus.publish(new OperationAdvanced(op.id(), op.status().name(), "RUNNING",
                operatorUserId == null ? "operator" : operatorUserId.toString(), now),
                correlationHeaders(op));
        metrics.mutationAccepted("audit-ops-service", "saga_retry");
    }

    private void handleAck(Operation op, RecordParticipantReplyCommand reply, Instant now) {
        if (op.targetRevision() != null && reply.ackedRevision() != null
                && reply.ackedRevision() < op.targetRevision()) {
            sagaRepo.transitionStep(op.id(), reply.participantService(), reply.stepName(),
                    StepStatus.FAILED, "projection.stale",
                    "Acked revision " + reply.ackedRevision() + " < target " + op.targetRevision(), now);
            handleFailure(op, withError(reply, "projection.stale",
                    "Acked revision below target"), now);
            return;
        }

        sagaRepo.transitionStep(op.id(), reply.participantService(), reply.stepName(),
                StepStatus.ACKED, null, null, now);

        audit.append(new AuditEvent(
                UUID.randomUUID(), op.id(), op.correlationId(),
                null, AuditEvent.ActorKind.SERVICE,
                "saga.step.acked",
                reply.participantService(), reply.stepName(),
                Map.of("sequenceNo", reply.stepName()), now, null));

        if (allRequiredStepsAcked(op.id())) {
            SagaTransitions.requireAllowed(op.status(), OperationStatus.SUCCEEDED);
            Operation advanced = operationRepo.transition(op.id(), op.version(),
                    OperationStatus.SUCCEEDED, null, null, now);
            eventBus.publish(new OperationAdvanced(op.id(), op.status().name(), "SUCCEEDED",
                    "orchestrator", now), correlationHeaders(op));
            metrics.mutationAcceptedCounter("audit-ops-service", "saga_succeeded").increment();
            LOG.info("Operation {} succeeded", advanced.id());
        } else if (op.status() == OperationStatus.PENDING) {
            SagaTransitions.requireAllowed(op.status(), OperationStatus.RUNNING);
            operationRepo.transition(op.id(), op.version(),
                    OperationStatus.RUNNING, null, null, now);
            eventBus.publish(new OperationAdvanced(op.id(), "PENDING", "RUNNING",
                    "orchestrator", now), correlationHeaders(op));
        }
    }

    private void handleFailure(Operation op, RecordParticipantReplyCommand reply, Instant now) {
        SagaStep step = sagaRepo.listSteps(op.id()).stream()
                .filter(s -> s.participantService().equals(reply.participantService())
                        && s.stepName().equals(reply.stepName()))
                .findFirst()
                .orElseThrow(() -> new SagaConflictException(
                        "Unknown step " + reply.participantService() + "/" + reply.stepName()));

        if (step.attemptCount() >= maxAttempts) {
            handleDeadLetter(op, reply, now);
            return;
        }

        sagaRepo.transitionStep(op.id(), reply.participantService(), reply.stepName(),
                StepStatus.FAILED,
                reply.errorCode(), reply.errorMessage(), now);
        long backoff = retryJitterMillis();
        commandBus.dispatchCommand(buildCommand(op, step, now, backoff));

        if (op.status() == OperationStatus.PENDING) {
            SagaTransitions.requireAllowed(op.status(), OperationStatus.RUNNING);
            operationRepo.transition(op.id(), op.version(),
                    OperationStatus.RUNNING, null, null, now);
        }
        metrics.mutationFailed("audit-ops-service", "saga_step",
                reply.errorCode() == null ? "unknown" : reply.errorCode());
    }

    private void handleCompensated(Operation op, RecordParticipantReplyCommand reply, Instant now) {
        sagaRepo.transitionStep(op.id(), reply.participantService(), reply.stepName(),
                StepStatus.COMPENSATED, null, null, now);
        if (allRequiredStepsCompensated(op.id())) {
            SagaTransitions.requireAllowed(op.status(), OperationStatus.COMPENSATED);
            operationRepo.transition(op.id(), op.version(),
                    OperationStatus.COMPENSATED, null, null, now);
            eventBus.publish(new OperationAdvanced(op.id(), "COMPENSATING", "COMPENSATED",
                    "orchestrator", now), correlationHeaders(op));
        }
    }

    private void handleDeadLetter(Operation op, RecordParticipantReplyCommand reply, Instant now) {
        sagaRepo.transitionStep(op.id(), reply.participantService(), reply.stepName(),
                StepStatus.DEAD_LETTERED, reply.errorCode(), reply.errorMessage(), now);
        sagaRepo.deadLetterStep(op.id(), reply.participantService(), reply.stepName(),
                reply.errorCode(), reply.errorMessage(), Map.of(), now);

        SagaTransitions.requireAllowed(op.status(), OperationStatus.MANUAL_REVIEW);
        operationRepo.transition(op.id(), op.version(),
                OperationStatus.MANUAL_REVIEW,
                reply.errorCode(), reply.errorMessage(), now);
        audit.append(new AuditEvent(
                UUID.randomUUID(), op.id(), op.correlationId(),
                null, AuditEvent.ActorKind.SERVICE,
                "operation.quarantined",
                reply.participantService(), reply.stepName(),
                Map.of("errorCode", reply.errorCode() == null ? "" : reply.errorCode(),
                        "errorMessage", reply.errorMessage() == null ? "" : reply.errorMessage()),
                now, null));
        eventBus.publish(new com.familya.auditops.domain.event.OperationQuarantined(
                op.id(), reply.errorCode() + ": " + reply.errorMessage(), now),
                correlationHeaders(op));
        metrics.mutationFailed("audit-ops-service", "saga_quarantined",
                reply.errorCode() == null ? "unknown" : reply.errorCode());
    }

    private boolean allRequiredStepsAcked(UUID operationId) {
        long total = sagaRepo.listSteps(operationId).size();
        long acked = sagaRepo.countByOperationAndStatus(operationId, StepStatus.ACKED);
        return total > 0 && acked == total;
    }

    private boolean allRequiredStepsCompensated(UUID operationId) {
        long total = sagaRepo.listSteps(operationId).size();
        long comp = sagaRepo.countByOperationAndStatus(operationId, StepStatus.COMPENSATED);
        return total > 0 && comp == total;
    }

    private SagaCommandBus.SagaCommand buildCommand(Operation op, SagaStep step, Instant now, long backoffMs) {
        Map<String, String> headers = new HashMap<>();
        headers.put("correlationId", op.correlationId() == null ? op.id().toString() : op.correlationId().toString());
        headers.put("operationId", op.id().toString());
        headers.put("stepName", step.stepName());
        return new SagaCommandBus.SagaCommand(
                op.id(), op.correlationId(),
                step.participantService(), step.stepName(), step.sequenceNo(),
                "saga.step.retry",
                "{\"retry\":true}",
                step.expectedVersion(),
                step.targetRevision(),
                step.targetEpoch(),
                now.plusMillis(backoffMs),
                headers);
    }

    private long retryJitterMillis() {
        long cap = Math.max(retryBaseMs, retryMaxMs);
        long base = Math.min(retryBaseMs, retryMaxMs);
        long exp = base;
        for (int i = 1; i < Math.min(6, Math.max(1, maxAttempts)); i++) {
            if (exp >= cap) break;
            exp *= 2;
        }
        long jitter = (long) (Math.random() * (exp - base + 1));
        return Math.min(cap, base + jitter);
    }

    private static RecordParticipantReplyCommand withError(RecordParticipantReplyCommand reply,
                                                            String code, String msg) {
        return new RecordParticipantReplyCommand(
                reply.operationId(), reply.participantService(), reply.stepName(),
                reply.outcome(),
                reply.ackedRevision(), reply.ackedEpoch(),
                reply.expectedVersion(),
                code, msg,
                reply.correlationId(), reply.causationId());
    }

    private static Map<String, String> correlationHeaders(Operation op) {
        Map<String, String> h = new HashMap<>();
        if (op.correlationId() != null) h.put("correlationId", op.correlationId().toString());
        h.put("operationId", op.id().toString());
        return h;
    }
}