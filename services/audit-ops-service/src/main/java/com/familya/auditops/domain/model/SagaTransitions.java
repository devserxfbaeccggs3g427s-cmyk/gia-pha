package com.familya.auditops.domain.model;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Saga state-machine transition guard. The transitions encoded here
 * implement the standard path described in
 * {@link OperationStatus#javadoc}.
 *
 * <p>This class is intentionally framework-free: it does not depend
 * on Spring, JDBC, or Kafka. The orchestrator consults it before
 * every persistence write so an illegal transition never reaches
 * the database. Compensating transitions take the operation to
 * {@link OperationStatus#COMPENSATED}; irreversible-boundary
 * violations are routed to {@link OperationStatus#MANUAL_REVIEW}.</p>
 */
public final class SagaTransitions {

    private static final Map<OperationStatus, Set<OperationStatus>> ALLOWED = Map.of(
            OperationStatus.PENDING, EnumSet.of(OperationStatus.RUNNING, OperationStatus.FAILED, OperationStatus.MANUAL_REVIEW),
            OperationStatus.RUNNING, EnumSet.of(OperationStatus.SUCCEEDED, OperationStatus.FAILED, OperationStatus.COMPENSATING, OperationStatus.MANUAL_REVIEW),
            OperationStatus.FAILED, EnumSet.of(OperationStatus.COMPENSATING, OperationStatus.MANUAL_REVIEW),
            OperationStatus.COMPENSATING, EnumSet.of(OperationStatus.COMPENSATED, OperationStatus.MANUAL_REVIEW),
            OperationStatus.SUCCEEDED, EnumSet.noneOf(OperationStatus.class),
            OperationStatus.COMPENSATED, EnumSet.noneOf(OperationStatus.class),
            OperationStatus.MANUAL_REVIEW, EnumSet.of(OperationStatus.RUNNING, OperationStatus.COMPENSATING, OperationStatus.COMPENSATED)
    );

    private SagaTransitions() { }

    public static boolean isAllowed(OperationStatus from, OperationStatus to) {
        if (from == to) {
            return false;
        }
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(OperationStatus.class)).contains(to);
    }

    public static void requireAllowed(OperationStatus from, OperationStatus to) {
        if (!isAllowed(from, to)) {
            throw new com.familya.auditops.domain.exception.SagaConflictException(
                    "Illegal Saga transition " + from + " -> " + to);
        }
    }
}