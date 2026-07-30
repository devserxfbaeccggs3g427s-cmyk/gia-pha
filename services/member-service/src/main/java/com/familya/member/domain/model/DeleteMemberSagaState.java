package com.familya.member.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Authoritative state of a delete-member Saga. Owned by the Member service per
 * ADR-003. {@code state} transitions are governed by
 * {@link com.familya.member.application.usecase.DeleteMemberSagaService}.
 */
public final class DeleteMemberSagaState {

    private final UUID operationId;
    private final UUID treeId;
    private final UUID memberId;
    private final UUID initiatingUserId;
    private final UUID correlationId;
    private State state;
    private final long targetAggregateVersion;
    private final long targetEpoch;
    private final Instant deadlineAt;
    private final Instant startedAt;
    private Instant finalizedAt;
    private Instant lastUpdatedAt;
    private Instant irreversibleAt;
    private String failureCode;
    private String failureMessage;

    public DeleteMemberSagaState(UUID operationId, UUID treeId, UUID memberId,
                                 UUID initiatingUserId, UUID correlationId,
                                 State state, long targetAggregateVersion, long targetEpoch,
                                 Instant deadlineAt, Instant startedAt, Instant finalizedAt,
                                 Instant lastUpdatedAt, Instant irreversibleAt,
                                 String failureCode, String failureMessage) {
        this.operationId = Objects.requireNonNull(operationId);
        this.treeId = Objects.requireNonNull(treeId);
        this.memberId = Objects.requireNonNull(memberId);
        this.initiatingUserId = Objects.requireNonNull(initiatingUserId);
        this.correlationId = Objects.requireNonNull(correlationId);
        this.state = Objects.requireNonNull(state);
        this.targetAggregateVersion = targetAggregateVersion;
        this.targetEpoch = targetEpoch;
        this.deadlineAt = Objects.requireNonNull(deadlineAt);
        this.startedAt = Objects.requireNonNull(startedAt);
        this.finalizedAt = finalizedAt;
        this.lastUpdatedAt = Objects.requireNonNull(lastUpdatedAt);
        this.irreversibleAt = irreversibleAt;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
    }

    public UUID operationId() { return operationId; }
    public UUID treeId() { return treeId; }
    public UUID memberId() { return memberId; }
    public UUID initiatingUserId() { return initiatingUserId; }
    public UUID correlationId() { return correlationId; }
    public State state() { return state; }
    public long targetAggregateVersion() { return targetAggregateVersion; }
    public long targetEpoch() { return targetEpoch; }
    public Instant deadlineAt() { return deadlineAt; }
    public Instant startedAt() { return startedAt; }
    public Instant finalizedAt() { return finalizedAt; }
    public Instant lastUpdatedAt() { return lastUpdatedAt; }
    public Instant irreversibleAt() { return irreversibleAt; }
    public String failureCode() { return failureCode; }
    public String failureMessage() { return failureMessage; }

    public void transitionTo(State next, Instant now) {
        State allowed = this.state.allowedNext(next);
        if (allowed == null) {
            throw new IllegalStateException(
                    "Illegal delete-member Saga transition " + this.state + " -> " + next);
        }
        this.state = next;
        this.lastUpdatedAt = now;
        if (next.isTerminal()) {
            this.finalizedAt = now;
        }
    }

    public void markIrreversible(Instant now) {
        if (this.irreversibleAt == null) {
            this.irreversibleAt = now;
            this.lastUpdatedAt = now;
        }
    }

    public void recordFailure(String code, String message, Instant now) {
        this.failureCode = code;
        this.failureMessage = message;
        this.lastUpdatedAt = now;
    }

    public enum State {
        PENDING,
        DISPATCHED,
        COMPENSATING,
        SUCCEEDED,
        FAILED,
        MANUAL_REVIEW,
        CANCELLED;

        public boolean isTerminal() {
            return this == SUCCEEDED || this == FAILED || this == MANUAL_REVIEW || this == CANCELLED;
        }

        public State allowedNext(State next) {
            return switch (this) {
                case PENDING       -> next == DISPATCHED || next == COMPENSATING || next == CANCELLED || next == MANUAL_REVIEW || next == FAILED ? next : null;
                case DISPATCHED    -> next == COMPENSATING || next == SUCCEEDED || next == FAILED || next == MANUAL_REVIEW || next == CANCELLED ? next : null;
                case COMPENSATING  -> next == SUCCEEDED || next == FAILED || next == MANUAL_REVIEW || next == CANCELLED ? next : null;
                case SUCCEEDED, FAILED, MANUAL_REVIEW, CANCELLED -> null;
            };
        }
    }
}