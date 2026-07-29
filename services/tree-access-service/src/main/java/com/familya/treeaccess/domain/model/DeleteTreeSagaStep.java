package com.familya.treeaccess.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class DeleteTreeSagaStep {

    private final UUID operationId;
    private final int sequenceNo;
    private final String stepCode;
    private final String participantService;
    private final boolean required;
    private final boolean compensatable;
    private State state;
    private int attemptCount;
    private final int maxAttempts;
    private Instant lastDispatchedAt;
    private Instant lastReplyAt;
    private Long appliedAggregateVersion;
    private Long appliedEpoch;
    private String failureCode;
    private String failureMessage;

    public DeleteTreeSagaStep(UUID operationId, int sequenceNo, String stepCode,
                              String participantService, boolean required, boolean compensatable,
                              State state, int attemptCount, int maxAttempts,
                              Instant lastDispatchedAt, Instant lastReplyAt,
                              Long appliedAggregateVersion, Long appliedEpoch,
                              String failureCode, String failureMessage) {
        this.operationId = Objects.requireNonNull(operationId);
        this.sequenceNo = sequenceNo;
        this.stepCode = Objects.requireNonNull(stepCode);
        this.participantService = Objects.requireNonNull(participantService);
        this.required = required;
        this.compensatable = compensatable;
        this.state = Objects.requireNonNull(state);
        this.attemptCount = attemptCount;
        this.maxAttempts = maxAttempts;
        this.lastDispatchedAt = lastDispatchedAt;
        this.lastReplyAt = lastReplyAt;
        this.appliedAggregateVersion = appliedAggregateVersion;
        this.appliedEpoch = appliedEpoch;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
    }

    public UUID operationId() { return operationId; }
    public int sequenceNo() { return sequenceNo; }
    public String stepCode() { return stepCode; }
    public String participantService() { return participantService; }
    public boolean required() { return required; }
    public boolean compensatable() { return compensatable; }
    public State state() { return state; }
    public int attemptCount() { return attemptCount; }
    public int maxAttempts() { return maxAttempts; }
    public Instant lastDispatchedAt() { return lastDispatchedAt; }
    public Instant lastReplyAt() { return lastReplyAt; }
    public Long appliedAggregateVersion() { return appliedAggregateVersion; }
    public Long appliedEpoch() { return appliedEpoch; }
    public String failureCode() { return failureCode; }
    public String failureMessage() { return failureMessage; }

    public void dispatch(Instant now) {
        this.state = State.DISPATCHED;
        this.attemptCount = this.attemptCount + 1;
        this.lastDispatchedAt = now;
    }

    public void ack(Instant now, long appliedAggregateVersion, long appliedEpoch) {
        this.state = State.ACK;
        this.lastReplyAt = now;
        this.appliedAggregateVersion = appliedAggregateVersion;
        this.appliedEpoch = appliedEpoch;
    }

    public void fail(String code, String message, Instant now) {
        this.state = State.FAILED;
        this.lastReplyAt = now;
        this.failureCode = code;
        this.failureMessage = message;
    }

    public boolean exhausted() { return attemptCount >= maxAttempts; }

    public enum State { PENDING, DISPATCHED, ACK, FAILED, COMPENSATED }
}