package com.familya.auditops.application.port.out;

import com.familya.auditops.domain.model.Operation;
import com.familya.auditops.domain.model.OperationStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for the operation projection. Reads are served
 * directly by the database; writes commit the new state and a
 * matching outbox row in a single local transaction.
 */
public interface OperationRepository {

    /**
     * Insert a brand-new operation in {@code PENDING}. Returns the
     * persisted aggregate including the generated id and version.
     */
    Operation insert(Operation operation);

    /**
     * Find an operation by id. Returned for polling and operator
     * tooling; never for business or authorization decisions.
     */
    Optional<Operation> findById(UUID operationId);

    /**
     * Atomic state transition. The repository reads the row with
     * {@code FOR UPDATE}, checks the supplied {@code expectedVersion},
     * applies the mutation, and bumps the version. Throws
     * {@link com.familya.platform.error.OptimisticConcurrencyException}
     * if the row was changed by another writer.
     */
    Operation transition(UUID operationId,
                         long expectedVersion,
                         OperationStatus next,
                         String errorCode,
                         String errorMessage,
                         java.time.Instant when);

    /**
     * Operator-supplied filter. Used by the operator console.
     */
    List<Operation> findByStatus(OperationStatus status, int limit);

    /**
     * Count operations in a non-terminal state, used by health and
     * by the cutover auto-stop rule.
     */
    long countByStatusIn(List<OperationStatus> statuses);
}