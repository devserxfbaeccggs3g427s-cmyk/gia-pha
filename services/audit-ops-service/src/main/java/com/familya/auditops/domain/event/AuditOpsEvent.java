package com.familya.auditops.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Base type for events the audit-ops service publishes back to the
 * platform. All such events partition by {@code operationId} so the
 * ordered projection stream is preserved per operation.
 */
public abstract class AuditOpsEvent {
    public abstract UUID operationId();
    public abstract String eventType();
    public abstract int eventVersion();
    public abstract Instant occurredAt();

    public String topic() { return "auditops.events.v1"; }
    public String partitionKey() { return operationId().toString(); }
}