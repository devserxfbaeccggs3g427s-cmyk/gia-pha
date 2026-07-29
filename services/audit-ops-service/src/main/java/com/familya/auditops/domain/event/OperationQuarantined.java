package com.familya.auditops.domain.event;

import java.time.Instant;
import java.util.UUID;

public final class OperationQuarantined extends AuditOpsEvent {
    private final UUID operationId;
    private final String reason;
    private final Instant occurredAt;
    private final int eventVersion;

    public OperationQuarantined(UUID operationId, String reason, Instant occurredAt) {
        this.operationId = operationId;
        this.reason = reason;
        this.occurredAt = occurredAt;
        this.eventVersion = 1;
    }

    @Override public UUID operationId() { return operationId; }
    @Override public String eventType() { return "auditops.operation.quarantined"; }
    @Override public int eventVersion() { return eventVersion; }
    @Override public Instant occurredAt() { return occurredAt; }

    public String reason() { return reason; }
}