package com.familya.auditops.application.port.in;

import java.util.Objects;
import java.util.UUID;

/**
 * Records a participant's reply against a Saga step. Issued by the
 * Saga reply consumer. The reply carries the participant's acked
 * revision/epoch so the orchestrator can enforce the target-revision
 * completion rule (Task 13).
 */
public final class RecordParticipantReplyCommand {

    public enum Outcome { ACKED, FAILED, COMPENSATED, DEAD_LETTERED }

    private final UUID operationId;
    private final String participantService;
    private final String stepName;
    private final Outcome outcome;
    private final Long ackedRevision;
    private final Long ackedEpoch;
    private final Long expectedVersion;
    private final String errorCode;
    private final String errorMessage;
    private final String correlationId;
    private final String causationId;

    public RecordParticipantReplyCommand(UUID operationId,
                                         String participantService,
                                         String stepName,
                                         Outcome outcome,
                                         Long ackedRevision,
                                         Long ackedEpoch,
                                         Long expectedVersion,
                                         String errorCode,
                                         String errorMessage,
                                         String correlationId,
                                         String causationId) {
        this.operationId = Objects.requireNonNull(operationId);
        this.participantService = Objects.requireNonNull(participantService);
        this.stepName = Objects.requireNonNull(stepName);
        this.outcome = Objects.requireNonNull(outcome);
        this.ackedRevision = ackedRevision;
        this.ackedEpoch = ackedEpoch;
        this.expectedVersion = expectedVersion;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.correlationId = correlationId;
        this.causationId = causationId;
    }

    public UUID operationId() { return operationId; }
    public String participantService() { return participantService; }
    public String stepName() { return stepName; }
    public Outcome outcome() { return outcome; }
    public Long ackedRevision() { return ackedRevision; }
    public Long ackedEpoch() { return ackedEpoch; }
    public Long expectedVersion() { return expectedVersion; }
    public String errorCode() { return errorCode; }
    public String errorMessage() { return errorMessage; }
    public String correlationId() { return correlationId; }
    public String causationId() { return causationId; }
}