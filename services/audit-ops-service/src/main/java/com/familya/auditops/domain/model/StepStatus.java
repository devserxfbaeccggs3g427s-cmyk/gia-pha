package com.familya.auditops.domain.model;

/**
 * Per-participant Saga step status. Mirrors {@link OperationStatus}
 * but scoped to a single step; an operation SUCCEEDS only when all
 * required steps have status {@link #ACKED}.
 */
public enum StepStatus {
    PENDING,
    DISPATCHED,
    ACKED,
    FAILED,
    COMPENSATED,
    DEAD_LETTERED;

    public boolean isTerminal() {
        return this == ACKED || this == COMPENSATED || this == DEAD_LETTERED;
    }
}