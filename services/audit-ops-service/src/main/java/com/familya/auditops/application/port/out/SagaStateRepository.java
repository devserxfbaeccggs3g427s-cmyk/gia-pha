package com.familya.auditops.application.port.out;

import com.familya.auditops.domain.model.SagaState;
import com.familya.auditops.domain.model.SagaStep;
import com.familya.auditops.domain.model.StepStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for the Saga state machine. The orchestrator
 * uses this port to read and write durable Saga state. All writes
 * commit inside the same local transaction as the operation
 * transition and the audit append.
 */
public interface SagaStateRepository {

    Optional<SagaState> findState(UUID operationId);

    SagaState saveState(SagaState state);

    List<SagaStep> listSteps(UUID operationId);

    SagaStep saveStep(SagaStep step);

    /**
     * Transition a step using optimistic concurrency. Throws
     * {@link com.familya.platform.error.OptimisticConcurrencyException}
     * on stale version.
     */
    SagaStep transitionStep(UUID operationId,
                            String participantService,
                            String stepName,
                            StepStatus next,
                            String errorCode,
                            String errorMessage,
                            java.time.Instant when);

    /**
     * Quarantine a step that exhausted retries; the orchestrator
     * MUST follow this with an operation transition to
     * {@code MANUAL_REVIEW} (Task 13 / ADR-003).
     */
    void deadLetterStep(UUID operationId,
                        String participantService,
                        String stepName,
                        String errorCode,
                        String errorMessage,
                        java.util.Map<String, Object> payload,
                        java.time.Instant when);

    /**
     * Count steps in a given status for a single operation. Used by
     * the target-revision completion rule.
     */
    long countByOperationAndStatus(UUID operationId, StepStatus status);
}