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
    private Instant nextAttemptAt;
    private Instant lastDispatchedAt;
    private Instant stepDeadlineAt;
    private UUID dispatchToken;
    private Instant lastFailureAt;
    private Instant lastReplyAt;
    private Long appliedAggregateVersion;
    private Long appliedEpoch;
    private String failureCode;
    private String failureMessage;

    public DeleteTreeSagaStep(UUID operationId, int sequenceNo, String stepCode,
                              String participantService, boolean required, boolean compensatable,
                              State state, int attemptCount, int maxAttempts,
                              Instant nextAttemptAt,
                              Instant lastDispatchedAt, Instant stepDeadlineAt,
                              UUID dispatchToken, Instant lastFailureAt,
                              Instant lastReplyAt,
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
        this.nextAttemptAt = nextAttemptAt;
        this.lastDispatchedAt = lastDispatchedAt;
        this.stepDeadlineAt = stepDeadlineAt;
        this.dispatchToken = dispatchToken;
        this.lastFailureAt = lastFailureAt;
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
    public Instant nextAttemptAt() { return nextAttemptAt; }
    public Instant lastDispatchedAt() { return lastDispatchedAt; }
    public Instant stepDeadlineAt() { return stepDeadlineAt; }
    public UUID dispatchToken() { return dispatchToken; }
    public Instant lastFailureAt() { return lastFailureAt; }
    public Instant lastReplyAt() { return lastReplyAt; }
    public Long appliedAggregateVersion() { return appliedAggregateVersion; }
    public Long appliedEpoch() { return appliedEpoch; }
    public String failureCode() { return failureCode; }
    public String failureMessage() { return failureMessage; }

    public boolean claimDispatch(UUID token, Instant now, Instant deadlineAt) {
        Objects.requireNonNull(token, "dispatchToken");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(deadlineAt, "stepDeadlineAt");
        if (isTerminal()) {
            return false;
        }
        if (state == State.DISPATCHED) {
            return token.equals(this.dispatchToken);
        }
        this.attemptCount = this.attemptCount + 1;
        this.state = State.DISPATCHED;
        this.dispatchToken = token;
        this.lastDispatchedAt = now;
        this.stepDeadlineAt = deadlineAt;
        this.nextAttemptAt = null;
        return true;
    }

    public void acknowledge(Instant now, long appliedAggregateVersion, long appliedEpoch) {
        Objects.requireNonNull(now, "now");
        if (state == State.ACK) {
            this.lastReplyAt = now;
            this.appliedAggregateVersion = appliedAggregateVersion;
            this.appliedEpoch = appliedEpoch;
            return;
        }
        this.state = State.ACK;
        this.lastReplyAt = now;
        this.appliedAggregateVersion = appliedAggregateVersion;
        this.appliedEpoch = appliedEpoch;
        this.nextAttemptAt = null;
        this.stepDeadlineAt = null;
    }

    public void scheduleRetry(Instant now, Instant nextAttemptAt, String code, String message) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
        if (isTerminal()) {
            return;
        }
        this.state = State.FAILED;
        this.lastReplyAt = now;
        this.lastFailureAt = now;
        this.failureCode = code;
        this.failureMessage = message;
        this.nextAttemptAt = nextAttemptAt;
        this.stepDeadlineAt = null;
        this.dispatchToken = null;
    }

    public void markCompensated(Instant now) {
        Objects.requireNonNull(now, "now");
        this.state = State.COMPENSATED;
        this.lastReplyAt = now;
        this.nextAttemptAt = null;
        this.stepDeadlineAt = null;
    }

    public void markFailed(String code, String message, Instant now) {
        Objects.requireNonNull(now, "now");
        this.state = State.DEAD_LETTERED;
        this.lastReplyAt = now;
        this.lastFailureAt = now;
        this.failureCode = code;
        this.failureMessage = message;
        this.nextAttemptAt = null;
        this.stepDeadlineAt = null;
    }

    public boolean exhausted() { return attemptCount >= maxAttempts; }

    public boolean retryDue(Instant now) {
        Objects.requireNonNull(now, "now");
        if (state != State.FAILED) return false;
        if (exhausted()) return false;
        if (nextAttemptAt == null) return false;
        return !now.isBefore(nextAttemptAt);
    }

    public boolean timedOut(Instant now) {
        Objects.requireNonNull(now, "now");
        if (state != State.DISPATCHED) return false;
        if (stepDeadlineAt == null) return false;
        return !now.isBefore(stepDeadlineAt);
    }

    public boolean isTerminal() {
        return state == State.ACK || state == State.COMPENSATED || state == State.DEAD_LETTERED;
    }

    public boolean markCompensationDispatched(Instant now, UUID token, Instant stepDeadlineAt) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(stepDeadlineAt, "stepDeadlineAt");
        if (isTerminal()) {
            return false;
        }
        if (state == State.ACK) {
            this.state = State.DISPATCHED;
            this.lastDispatchedAt = now;
            this.dispatchToken = token;
            this.stepDeadlineAt = stepDeadlineAt;
            this.nextAttemptAt = null;
            return true;
        }
        return false;
    }

    public void dispatch(Instant now) {
        Objects.requireNonNull(now, "now");
        if (isTerminal()) return;
        if (state == State.DISPATCHED) {
            this.lastDispatchedAt = now;
            return;
        }
        this.attemptCount = this.attemptCount + 1;
        this.state = State.DISPATCHED;
        this.lastDispatchedAt = now;
        this.nextAttemptAt = null;
    }

    public void ack(Instant now, long appliedAggregateVersion, long appliedEpoch) {
        acknowledge(now, appliedAggregateVersion, appliedEpoch);
    }

    public void fail(String code, String message, Instant now) {
        Objects.requireNonNull(now, "now");
        if (isTerminal()) return;
        this.state = State.FAILED;
        this.lastReplyAt = now;
        this.lastFailureAt = now;
        this.failureCode = code;
        this.failureMessage = message;
    }

    public void compensate(Instant now) {
        markCompensated(now);
    }

    public void markDeadLettered(String code, String message, Instant now) {
        markFailed(code, message, now);
    }

    public enum State { PENDING, DISPATCHED, ACK, FAILED, COMPENSATED, DEAD_LETTERED }
}
