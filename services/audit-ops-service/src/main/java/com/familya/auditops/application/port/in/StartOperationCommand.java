package com.familya.auditops.application.port.in;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Command to start a new cross-service operation. Issued by an
 * owning service through the {@code OperationService} port. Returns
 * the new operation id and the assigned correlation id.
 */
public final class StartOperationCommand {

    private final UUID actingUser;
    private final String operationType;
    private final String aggregateType;
    private final String aggregateId;
    private final UUID treeId;
    private final Long targetRevision;
    private final Long targetEpoch;
    private final Map<String, Object> detail;
    private final String idempotencyKey;
    private final String payloadHash;
    private final String correlationIdHeader;
    private final Instant clientStartedAt;

    public StartOperationCommand(UUID actingUser,
                                 String operationType,
                                 String aggregateType,
                                 String aggregateId,
                                 UUID treeId,
                                 Long targetRevision,
                                 Long targetEpoch,
                                 Map<String, Object> detail,
                                 String idempotencyKey,
                                 String payloadHash,
                                 String correlationIdHeader,
                                 Instant clientStartedAt) {
        this.actingUser = actingUser;
        this.operationType = Objects.requireNonNull(operationType);
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.treeId = treeId;
        this.targetRevision = targetRevision;
        this.targetEpoch = targetEpoch;
        this.detail = detail == null ? Map.of() : Map.copyOf(detail);
        this.idempotencyKey = idempotencyKey;
        this.payloadHash = payloadHash;
        this.correlationIdHeader = correlationIdHeader;
        this.clientStartedAt = clientStartedAt;
    }

    public UUID actingUser() { return actingUser; }
    public String operationType() { return operationType; }
    public String aggregateType() { return aggregateType; }
    public String aggregateId() { return aggregateId; }
    public UUID treeId() { return treeId; }
    public Long targetRevision() { return targetRevision; }
    public Long targetEpoch() { return targetEpoch; }
    public Map<String, Object> detail() { return detail; }
    public String idempotencyKey() { return idempotencyKey; }
    public String payloadHash() { return payloadHash; }
    public String correlationIdHeader() { return correlationIdHeader; }
    public Instant clientStartedAt() { return clientStartedAt; }
}