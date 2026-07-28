package com.familya.auditops.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Operation aggregate root. One instance per cross-service mutation
 * recorded by the owning service. The aggregate is the public,
 * queryable projection that the Gateway polls
 * ({@code GET /api/v2/operations/{id}}) and that the operator console
 * reads for retry / manual-review actions.
 *
 * <p>This service is NOT the business authority for any domain — it
 * mirrors transitions as events arrive on the Saga reply topic. The
 * owning service holds the source of truth for the operation in its
 * own database; this projection exists for polling, audit, and
 * operator tooling only (ADR-003).</p>
 */
public final class Operation {

    private final UUID id;
    private final UUID correlationId;
    private final String service;
    private final String operationType;
    private OperationStatus status;
    private final Long targetRevision;
    private final Long targetEpoch;
    private final String aggregateType;
    private final String aggregateId;
    private final UUID treeId;
    private final UUID actingUser;
    private final Map<String, Object> detail;
    private final String errorCode;
    private final String errorMessage;
    private final Instant startedAt;
    private Instant updatedAt;
    private Instant finishedAt;
    private long version;

    public Operation(UUID id,
                     UUID correlationId,
                     String service,
                     String operationType,
                     OperationStatus status,
                     Long targetRevision,
                     Long targetEpoch,
                     String aggregateType,
                     String aggregateId,
                     UUID treeId,
                     UUID actingUser,
                     Map<String, Object> detail,
                     String errorCode,
                     String errorMessage,
                     Instant startedAt,
                     Instant updatedAt,
                     Instant finishedAt,
                     long version) {
        this.id = Objects.requireNonNull(id);
        this.correlationId = correlationId;
        this.service = Objects.requireNonNull(service);
        this.operationType = Objects.requireNonNull(operationType);
        this.status = Objects.requireNonNull(status);
        this.targetRevision = targetRevision;
        this.targetEpoch = targetEpoch;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.treeId = treeId;
        this.actingUser = actingUser;
        this.detail = detail == null ? Map.of() : Map.copyOf(detail);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.startedAt = Objects.requireNonNull(startedAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.finishedAt = finishedAt;
        this.version = version;
    }

    public UUID id() { return id; }
    public UUID correlationId() { return correlationId; }
    public String service() { return service; }
    public String operationType() { return operationType; }
    public OperationStatus status() { return status; }
    public Long targetRevision() { return targetRevision; }
    public Long targetEpoch() { return targetEpoch; }
    public String aggregateType() { return aggregateType; }
    public String aggregateId() { return aggregateId; }
    public UUID treeId() { return treeId; }
    public UUID actingUser() { return actingUser; }
    public Map<String, Object> detail() { return detail; }
    public String errorCode() { return errorCode; }
    public String errorMessage() { return errorMessage; }
    public Instant startedAt() { return startedAt; }
    public Instant updatedAt() { return updatedAt; }
    public Instant finishedAt() { return finishedAt; }
    public long version() { return version; }

    /**
     * Apply a state transition in-memory. The caller is responsible
     * for persisting the new state through the repository and for
     * having verified the transition is legal under the Saga state
     * machine. {@code updatedAt} is supplied by the caller to keep
     * the aggregate framework-independent.
     */
    public void applyTransition(OperationStatus next,
                                 Instant updatedAt) {
        this.status = next;
        this.version = this.version + 1;
        this.updatedAt = updatedAt;
        if (next.isTerminal()) {
            this.finishedAt = updatedAt;
        }
    }

    /**
     * Convenience factory for the public polling projection. Maps
     * to {@link com.familya.platform.api.AsyncOperation}.
     */
    public com.familya.platform.api.AsyncOperation toAsyncOperation() {
        com.familya.platform.api.AsyncOperation.Status api =
                com.familya.platform.api.AsyncOperation.Status.valueOf(status.name());
        return new com.familya.platform.api.AsyncOperation(
                id, api,
                "/api/v2/operations/" + id,
                status == OperationStatus.SUCCEEDED ? detail : null,
                status == OperationStatus.FAILED || status == OperationStatus.MANUAL_REVIEW
                        ? new com.familya.platform.api.AsyncOperation.ErrorBody(
                                errorCode == null ? "operation.failed" : errorCode,
                                errorMessage == null ? "Operation did not succeed" : errorMessage,
                                null, null)
                        : null,
                null,
                updatedAt);
    }
}