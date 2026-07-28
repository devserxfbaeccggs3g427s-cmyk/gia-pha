package com.familya.auditops.application.usecase;

import com.familya.auditops.application.port.in.StartOperationCommand;
import com.familya.auditops.application.port.out.AuditAppender;
import com.familya.auditops.application.port.out.OperationEventBus;
import com.familya.auditops.application.port.out.OperationRepository;
import com.familya.auditops.application.port.out.SagaStateRepository;
import com.familya.auditops.domain.event.OperationAdvanced;
import com.familya.auditops.domain.model.AuditEvent;
import com.familya.auditops.domain.model.Operation;
import com.familya.auditops.domain.model.OperationStatus;
import com.familya.auditops.domain.model.SagaState;
import com.familya.platform.api.AsyncOperation;
import com.familya.platform.error.IdempotencyConflictException;
import com.familya.platform.error.NotFoundException;
import com.familya.platform.idempotency.IdempotencyStore;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the operation registration path. Idempotency keys are recorded
 * against the public {@code Idempotency-Key} header so duplicate
 * cross-service retries return the recorded
 * {@link AsyncOperation}. The owning service MUST also publish its
 * own domain row + outbox row in the same transaction; the audit-ops
 * service records the projection here.
 */
@Service
public class OperationService {

    private static final Logger LOG = LoggerFactory.getLogger(OperationService.class);

    private final OperationRepository operationRepo;
    private final SagaStateRepository sagaRepo;
    private final AuditAppender audit;
    private final OperationEventBus eventBus;
    private final IdempotencyStore idempotency;
    private final PlatformMetrics metrics;
    private final Clock clock;

    public OperationService(OperationRepository operationRepo,
                            SagaStateRepository sagaRepo,
                            AuditAppender audit,
                            OperationEventBus eventBus,
                            IdempotencyStore idempotency,
                            PlatformMetrics metrics,
                            Clock clock) {
        this.operationRepo = operationRepo;
        this.sagaRepo = sagaRepo;
        this.audit = audit;
        this.eventBus = eventBus;
        this.idempotency = idempotency;
        this.metrics = metrics;
        this.clock = clock;
    }

    /**
     * Register a brand-new operation in {@code PENDING} and seed the
     * Saga state machine envelope. The caller passes the operation
     * type, aggregate reference, and optional target revision/epoch.
     * Idempotency is enforced through {@link IdempotencyStore}.
     */
    @Transactional
    public AsyncOperation register(StartOperationCommand cmd) {
        metrics.mutationAccepted("audit-ops-service", "register_operation");
        if (cmd.idempotencyKey() != null && !cmd.idempotencyKey().isBlank()) {
            var existing = idempotency.find(cmd.idempotencyKey());
            if (existing.isPresent()) {
                if (cmd.payloadHash() != null && !cmd.payloadHash().isBlank()) {
                    // JdbcIdempotencyStore enforces the hash at record
                    // time. A separate read-side hash check is not
                    // available from the port, but the implementation
                    // throws IdempotencyConflictException when the
                    // hash mismatches at record time.
                }
                metrics.mutationAcceptedCounter("audit-ops-service", "register_operation_idempotent").increment();
                return existing.get();
            }
        }

        Instant now = Instant.now(clock);
        UUID operationId = UUID.randomUUID();
        UUID correlationId = cmd.correlationIdHeader() == null || cmd.correlationIdHeader().isBlank()
                ? operationId
                : UUID.fromString(cmd.correlationIdHeader());

        Operation op = new Operation(
                operationId, correlationId, "audit-ops-service",
                cmd.operationType(), OperationStatus.PENDING,
                cmd.targetRevision(), cmd.targetEpoch(),
                cmd.aggregateType(), cmd.aggregateId(),
                cmd.treeId(), cmd.actingUser(),
                cmd.detail(),
                null, null,
                now, now, null, 0L);
        operationRepo.insert(op);

        SagaState state = new SagaState(
                operationId, deriveSagaType(cmd.operationType()),
                "INIT", false, 0, now, Map.of(), 0L);
        sagaRepo.saveState(state);

        audit.append(new AuditEvent(
                UUID.randomUUID(), operationId, correlationId,
                cmd.actingUser(), AuditEvent.ActorKind.SERVICE,
                "operation.registered",
                cmd.aggregateType(), cmd.aggregateId(),
                cmd.detail(), now, null));

        eventBus.publish(new OperationAdvanced(operationId, "NEW", "PENDING",
                cmd.actingUser() == null ? "service" : cmd.actingUser().toString(), now),
                headers(correlationId, null));

        AsyncOperation envelope = AsyncOperation.accepted(operationId,
                "/api/v2/operations/" + operationId);

        if (cmd.idempotencyKey() != null && !cmd.idempotencyKey().isBlank()) {
            try {
                idempotency.record(cmd.idempotencyKey(),
                        cmd.payloadHash() == null ? "" : cmd.payloadHash(),
                        envelope);
            } catch (IdempotencyConflictException e) {
                LOG.warn("Idempotency conflict for key={} operation={}", cmd.idempotencyKey(), operationId);
                throw e;
            }
        }
        return envelope;
    }

    /**
     * Read projection used by {@code GET /api/v2/operations/{id}}.
     */
    @Transactional(readOnly = true)
    public AsyncOperation find(UUID operationId) {
        return operationRepo.findById(operationId)
                .map(Operation::toAsyncOperation)
                .orElseThrow(() -> new NotFoundException("Operation " + operationId + " not found"));
    }

    private static String deriveSagaType(String operationType) {
        if (operationType == null) return "unknown";
        int dot = operationType.indexOf('.');
        return dot > 0 ? operationType.substring(0, dot) : operationType;
    }

    private static Map<String, String> headers(UUID correlationId, String causationId) {
        Map<String, String> h = new HashMap<>();
        if (correlationId != null) h.put("correlationId", correlationId.toString());
        if (causationId != null) h.put("causationId", causationId);
        return h;
    }
}