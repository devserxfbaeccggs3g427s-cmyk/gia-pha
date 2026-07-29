package com.familya.treeaccess.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Tree aggregate root. Owns lifecycle (ACTIVE, FROZEN, TOMBSTONED) and
 * the authoritative revision/epoch. The owner_user_id is immutable;
 * the membership layer keeps the owner effective ADMIN via
 * {@link TreeMembership#effectiveRole()}.
 */
public final class Tree {

    private final UUID id;
    private String name;
    private final UUID ownerUserId;
    private State state;
    private long revision;
    private long epoch;
    private final Instant createdAt;
    private Instant frozenAt;
    private Instant tombstonedAt;
    private long version;

    public Tree(UUID id, String name, UUID ownerUserId, State state, long revision, long epoch,
                Instant createdAt, Instant frozenAt, Instant tombstonedAt, long version) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.ownerUserId = Objects.requireNonNull(ownerUserId);
        this.state = Objects.requireNonNull(state);
        this.revision = revision;
        this.epoch = epoch;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.frozenAt = frozenAt;
        this.tombstonedAt = tombstonedAt;
        this.version = version;
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public UUID ownerUserId() { return ownerUserId; }
    public State state() { return state; }
    public long revision() { return revision; }
    public long epoch() { return epoch; }
    public Instant createdAt() { return createdAt; }
    public Instant frozenAt() { return frozenAt; }
    public Instant tombstonedAt() { return tombstonedAt; }
    public long version() { return version; }

    public boolean isActive() { return state == State.ACTIVE; }
    public boolean isFrozen() { return state == State.FROZEN; }
    public boolean isTombstoned() { return state == State.TOMBSTONED; }

    public void rename(String newName, long expectedVersion) {
        requireVersion(expectedVersion);
        requireMutable("rename");
        this.name = Objects.requireNonNull(newName);
        bump();
    }

    public void freeze(long expectedVersion) {
        requireVersion(expectedVersion);
        if (state != State.ACTIVE) {
            throw new IllegalStateException("Tree is not ACTIVE (state=" + state + ")");
        }
        this.state = State.FROZEN;
        this.frozenAt = Instant.now();
        bump();
    }

    public void unfreeze(long expectedVersion) {
        requireVersion(expectedVersion);
        if (state != State.FROZEN) {
            throw new IllegalStateException("Tree is not FROZEN (state=" + state + ")");
        }
        this.state = State.ACTIVE;
        this.frozenAt = null;
        bump();
    }

    public void tombstone(long expectedVersion) {
        requireVersion(expectedVersion);
        if (state == State.TOMBSTONED) {
            return;
        }
        this.state = State.TOMBSTONED;
        this.tombstonedAt = Instant.now();
        bump();
    }

    /**
     * Advance the revision and epoch. Called by the orchestrator after
     * a participant acknowledges reaching the target revision. The
     * epoch is the cross-service coordination boundary; a higher epoch
     * means any earlier staged work is hidden.
     */
    public void advanceRevision(long expectedVersion, long newRevision, long newEpoch) {
        requireVersion(expectedVersion);
        requireMutable("advanceRevision");
        if (newRevision <= this.revision) {
            throw new IllegalArgumentException(
                    "newRevision " + newRevision + " must be greater than current " + this.revision);
        }
        if (newEpoch < this.epoch) {
            throw new IllegalArgumentException(
                    "newEpoch " + newEpoch + " must be >= current " + this.epoch);
        }
        this.revision = newRevision;
        this.epoch = newEpoch;
        bump();
    }

    /**
     * Advance the revision/epoch for a Saga participant command. Unlike
     * {@link #advanceRevision(long, long, long)}, this method does NOT
     * require the tree to be ACTIVE because delete-member and delete-tree
     * Sagas operate on a FROZEN or PENDING_DELETION tree.
     */
    public void advanceRevisionForSaga(long expectedVersion, long newRevision, long newEpoch) {
        requireVersion(expectedVersion);
        if (state == State.TOMBSTONED) {
            throw new IllegalStateException("Tree is TOMBSTONED; cannot advance revision");
        }
        if (newRevision <= this.revision) {
            throw new IllegalArgumentException(
                    "newRevision " + newRevision + " must be greater than current " + this.revision);
        }
        if (newEpoch < this.epoch) {
            throw new IllegalArgumentException(
                    "newEpoch " + newEpoch + " must be >= current " + this.epoch);
        }
        this.revision = newRevision;
        this.epoch = newEpoch;
        bump();
    }

    public void requireMutable(String op) {
        if (state != State.ACTIVE) {
            throw new IllegalStateException(
                    "Tree " + id + " cannot be " + op + " in state " + state);
        }
    }

    private void requireVersion(long expected) {
        if (this.version != expected) {
            throw new com.familya.platform.error.OptimisticConcurrencyException(
                    "Tree " + id + " version " + this.version + " != expected " + expected);
        }
    }

    private void bump() {
        this.version = this.version + 1;
    }

    public enum State {
        ACTIVE,
        FROZEN,
        TOMBSTONED,
        DELETE_FROZEN,
        PENDING_DELETION,
        DELETION_FINALIZED;

        public boolean isActive()   { return this == ACTIVE; }
        public boolean isFrozen()   { return this == FROZEN || this == DELETE_FROZEN; }
        public boolean isTombstoned(){ return this == TOMBSTONED || this == DELETION_FINALIZED; }
        public boolean isPendingDeletion() { return this == PENDING_DELETION; }
    }
}