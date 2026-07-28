package com.familya.auditops.domain.event;

import java.time.Instant;
import java.util.UUID;

public final class OperationAdvanced extends AuditOpsEvent {
    private final UUID operationId;
    private final String fromStatus;
    private final String toStatus;
    private final String actor;
    private final Instant occurredAt;
    private final int eventVersion;

    public OperationAdvanced(UUID operationId, String fromStatus, String toStatus,
                             String actor, Instant occurredAt) {
        this.operationId = operationId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actor = actor;
        this.occurredAt = occurredAt;
        this.eventVersion = 1;
    }

    @Override public UUID operationId() { return operationId; }
    @Override public String eventType() { return "auditops.operation.advanced"; }
    @Override public int eventVersion() { return eventVersion; }
    @Override public Instant occurredAt() { return occurredAt; }

    public String fromStatus() { return fromStatus; }
    public String toStatus() { return toStatus; }
    public String actor() { return actor; }
}