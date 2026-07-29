package com.familya.auditops.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Saga step. One row per (operation, participant, stepName).
 *
 * <p>The {@code expectedVersion} is the version the orchestrator read
 * from the source command at dispatch time. The participant MUST ack
 * with {@code targetRevision} &gt;= {@code expectedVersion} and
 * {@code targetEpoch} &gt;= {@code step.targetEpoch} for the step to
 * count as {@link StepStatus#ACKED}.</p>
 */
public final class SagaStep {

    private final UUID operationId;
    private final String participantService;
    private final String stepName;
    private final int sequenceNo;
    private StepStatus status;
    private final Long targetRevision;
    private final Long targetEpoch;
    private final Long expectedVersion;
    private Instant ackedAt;
    private int attemptCount;
    private String lastErrorCode;
    private String lastErrorMessage;
    private Instant lastAttemptedAt;
    private final Map<String, Object> detail;

    public SagaStep(UUID operationId,
                    String participantService,
                    String stepName,
                    int sequenceNo,
                    StepStatus status,
                    Long targetRevision,
                    Long targetEpoch,
                    Long expectedVersion,
                    Instant ackedAt,
                    int attemptCount,
                    String lastErrorCode,
                    String lastErrorMessage,
                    Instant lastAttemptedAt,
                    Map<String, Object> detail) {
        this.operationId = Objects.requireNonNull(operationId);
        this.participantService = Objects.requireNonNull(participantService);
        this.stepName = Objects.requireNonNull(stepName);
        this.sequenceNo = sequenceNo;
        this.status = Objects.requireNonNull(status);
        this.targetRevision = targetRevision;
        this.targetEpoch = targetEpoch;
        this.expectedVersion = expectedVersion;
        this.ackedAt = ackedAt;
        this.attemptCount = attemptCount;
        this.lastErrorCode = lastErrorCode;
        this.lastErrorMessage = lastErrorMessage;
        this.lastAttemptedAt = lastAttemptedAt;
        this.detail = detail == null ? Map.of() : Map.copyOf(detail);
    }

    public UUID operationId() { return operationId; }
    public String participantService() { return participantService; }
    public String stepName() { return stepName; }
    public int sequenceNo() { return sequenceNo; }
    public StepStatus status() { return status; }
    public Long targetRevision() { return targetRevision; }
    public Long targetEpoch() { return targetEpoch; }
    public Long expectedVersion() { return expectedVersion; }
    public Instant ackedAt() { return ackedAt; }
    public int attemptCount() { return attemptCount; }
    public String lastErrorCode() { return lastErrorCode; }
    public String lastErrorMessage() { return lastErrorMessage; }
    public Instant lastAttemptedAt() { return lastAttemptedAt; }
    public Map<String, Object> detail() { return detail; }

    public void markDispatched(Instant when) {
        this.status = StepStatus.DISPATCHED;
        this.attemptCount = this.attemptCount + 1;
        this.lastAttemptedAt = when;
    }

    public void markAcked(Instant when) {
        this.status = StepStatus.ACKED;
        this.ackedAt = when;
    }

    public void markFailed(String code, String message, Instant when) {
        this.status = StepStatus.FAILED;
        this.lastErrorCode = code;
        this.lastErrorMessage = message;
        this.lastAttemptedAt = when;
    }

    public void markCompensated(Instant when) {
        this.status = StepStatus.COMPENSATED;
        this.lastAttemptedAt = when;
    }

    public void markDeadLettered(String code, String message, Instant when) {
        this.status = StepStatus.DEAD_LETTERED;
        this.lastErrorCode = code;
        this.lastErrorMessage = message;
        this.lastAttemptedAt = when;
    }
}