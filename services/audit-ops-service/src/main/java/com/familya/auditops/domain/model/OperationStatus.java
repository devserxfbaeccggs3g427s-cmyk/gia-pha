package com.familya.auditops.domain.model;

/**
 * Standard Saga lifecycle (ADR-007 / Task 13).
 *
 * <p>Linear path: {@code PENDING → RUNNING → SUCCEEDED}.</p>
 * <p>Failure path: {@code RUNNING → FAILED → COMPENSATING → COMPENSATED}.</p>
 * <p>Operator path: any non-terminal state may transition to
 * {@code MANUAL_REVIEW} when the orchestrator detects an unrecoverable
 * condition (participant outage beyond policy, payload poison,
 * irreversible boundary violated).</p>
 */
public enum OperationStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    COMPENSATING,
    COMPENSATED,
    MANUAL_REVIEW;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == COMPENSATED || this == MANUAL_REVIEW;
    }

    public boolean isCompensating() {
        return this == COMPENSATING || this == COMPENSATED;
    }

    public boolean isFailed() {
        return this == FAILED || this == COMPENSATING || this == COMPENSATED || this == MANUAL_REVIEW;
    }
}