package com.familya.auditops.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Operator projection of an owner's Saga lifecycle event. This row is NOT
 * authoritative — the Saga owner publishes the OperationStarted /
 * OperationStateChanged events and Audit Ops consumes them. See ADR-003.
 */
public final class OperationLifecycleRow {

    private final UUID operationId;
    private final String ownerService;
    private final String sagaType;
    private final UUID treeId;
    private final UUID initiatingUserId;
    private final String state;
    private final Long targetVersion;
    private final Long targetEpoch;
    private final String failureCode;
    private final String failureMessage;
    private final Instant startedAt;
    private final Instant updatedAt;
    private final Instant finalizedAt;

    public OperationLifecycleRow(UUID operationId, String ownerService, String sagaType,
                                 UUID treeId, UUID initiatingUserId, String state,
                                 Long targetVersion, Long targetEpoch,
                                 String failureCode, String failureMessage,
                                 Instant startedAt, Instant updatedAt, Instant finalizedAt) {
        this.operationId = Objects.requireNonNull(operationId);
        this.ownerService = Objects.requireNonNull(ownerService);
        this.sagaType = Objects.requireNonNull(sagaType);
        this.treeId = treeId;
        this.initiatingUserId = initiatingUserId;
        this.state = Objects.requireNonNull(state);
        this.targetVersion = targetVersion;
        this.targetEpoch = targetEpoch;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.startedAt = Objects.requireNonNull(startedAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.finalizedAt = finalizedAt;
    }

    public UUID operationId() { return operationId; }
    public String ownerService() { return ownerService; }
    public String sagaType() { return sagaType; }
    public UUID treeId() { return treeId; }
    public UUID initiatingUserId() { return initiatingUserId; }
    public String state() { return state; }
    public Long targetVersion() { return targetVersion; }
    public Long targetEpoch() { return targetEpoch; }
    public String failureCode() { return failureCode; }
    public String failureMessage() { return failureMessage; }
    public Instant startedAt() { return startedAt; }
    public Instant updatedAt() { return updatedAt; }
    public Instant finalizedAt() { return finalizedAt; }
}