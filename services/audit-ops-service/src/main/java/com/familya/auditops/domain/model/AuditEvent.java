package com.familya.auditops.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Append-only audit event. Once appended the row MUST NOT be
 * updated or deleted by application code. Retention is enforced by
 * an approved scheduled job (Task 17 / ADR-007).
 */
public final class AuditEvent {

    public enum ActorKind {
        USER, OPERATOR, SERVICE
    }

    private final UUID auditId;
    private final UUID operationId;
    private final UUID correlationId;
    private final UUID actorUserId;
    private final ActorKind actorKind;
    private final String action;
    private final String targetType;
    private final String targetId;
    private final Map<String, Object> detail;
    private final Instant occurredAt;
    private final String traceId;

    public AuditEvent(UUID auditId,
                      UUID operationId,
                      UUID correlationId,
                      UUID actorUserId,
                      ActorKind actorKind,
                      String action,
                      String targetType,
                      String targetId,
                      Map<String, Object> detail,
                      Instant occurredAt,
                      String traceId) {
        this.auditId = Objects.requireNonNull(auditId);
        this.operationId = operationId;
        this.correlationId = correlationId;
        this.actorUserId = actorUserId;
        this.actorKind = Objects.requireNonNull(actorKind);
        this.action = Objects.requireNonNull(action);
        this.targetType = targetType;
        this.targetId = targetId;
        this.detail = detail == null ? Map.of() : Map.copyOf(detail);
        this.occurredAt = Objects.requireNonNull(occurredAt);
        this.traceId = traceId;
    }

    public UUID auditId() { return auditId; }
    public UUID operationId() { return operationId; }
    public UUID correlationId() { return correlationId; }
    public UUID actorUserId() { return actorUserId; }
    public ActorKind actorKind() { return actorKind; }
    public String action() { return action; }
    public String targetType() { return targetType; }
    public String targetId() { return targetId; }
    public Map<String, Object> detail() { return detail; }
    public Instant occurredAt() { return occurredAt; }
    public String traceId() { return traceId; }
}