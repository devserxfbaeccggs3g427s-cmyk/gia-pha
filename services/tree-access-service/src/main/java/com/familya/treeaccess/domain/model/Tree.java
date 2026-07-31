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

    /**
     * @param id            mã cây
     * @param name          tên cây
     * @param ownerUserId   UUID chủ sở hữu (bất biến)
     * @param state         trạng thái
     * @param revision      revision hiện tại
     * @param epoch         epoch hiện tại
     * @param createdAt     thời điểm tạo
     * @param frozenAt      thời điểm đóng băng hoặc {@code null}
     * @param tombstonedAt  thời điểm tombstone hoặc {@code null}
     * @param version       phiên bản cho optimistic locking
     */
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

    /**
     * @return mã cây
     */
    public UUID id() { return id; }
    /**
     * @return tên cây
     */
    public String name() { return name; }
    /**
     * @return UUID chủ sở hữu (bất biến)
     */
    public UUID ownerUserId() { return ownerUserId; }
    /**
     * @return trạng thái vòng đời hiện tại
     */
    public State state() { return state; }
    /**
     * @return revision hiện tại của cây
     */
    public long revision() { return revision; }
    /**
     * @return epoch hiện tại của cây
     */
    public long epoch() { return epoch; }
    /**
     * @return thời điểm tạo cây
     */
    public Instant createdAt() { return createdAt; }
    /**
     * @return thời điểm đóng băng hoặc {@code null}
     */
    public Instant frozenAt() { return frozenAt; }
    /**
     * @return thời điểm đánh tombstone hoặc {@code null}
     */
    public Instant tombstonedAt() { return tombstonedAt; }
    /**
     * @return phiên bản cho optimistic locking
     */
    public long version() { return version; }

    /**
     * @return {@code true} nếu cây đang ACTIVE
     */
    public boolean isActive() { return state == State.ACTIVE; }
    /**
     * @return {@code true} nếu cây đang ở trạng thái đóng băng (kể cả DELETE_FROZEN)
     */
    public boolean isFrozen() { return state == State.FROZEN; }
    /**
     * @return {@code true} nếu cây đã bị tombstone
     */
    public boolean isTombstoned() { return state == State.TOMBSTONED; }

    /**
     * Đổi tên cây (chỉ được phép khi ACTIVE).
     *
     * @param newName         tên mới
     * @param expectedVersion phiên bản kỳ vọng cho optimistic locking
     */
    public void rename(String newName, long expectedVersion) {
        requireVersion(expectedVersion);
        requireMutable("rename");
        this.name = Objects.requireNonNull(newName);
        bump();
    }

    /**
     * Đóng băng cây (chỉ khi đang ACTIVE).
     *
     * @param expectedVersion phiên bản kỳ vọng
     */
    public void freeze(long expectedVersion) {
        requireVersion(expectedVersion);
        if (state != State.ACTIVE) {
            throw new IllegalStateException("Tree is not ACTIVE (state=" + state + ")");
        }
        this.state = State.FROZEN;
        this.frozenAt = Instant.now();
        bump();
    }

    /**
     * Bỏ đóng băng (chỉ khi đang FROZEN).
     *
     * @param expectedVersion phiên bản kỳ vọng
     */
    public void unfreeze(long expectedVersion) {
        requireVersion(expectedVersion);
        if (state != State.FROZEN) {
            throw new IllegalStateException("Tree is not FROZEN (state=" + state + ")");
        }
        this.state = State.ACTIVE;
        this.frozenAt = null;
        bump();
    }

    /**
     * Đánh dấu tombstone. Idempotent nếu đã ở TOMBSTONED.
     *
     * @param expectedVersion phiên bản kỳ vọng
     */
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

    /**
     * Bắt buộc cây phải đang ACTIVE để có thể mutate.
     *
     * @param op tên thao tác — chỉ dùng cho thông điệp lỗi
     * @throws IllegalStateException nếu cây không ACTIVE
     */
    public void requireMutable(String op) {
        if (state != State.ACTIVE) {
            throw new IllegalStateException(
                    "Tree " + id + " cannot be " + op + " in state " + state);
        }
    }

    /**
     * So khớp phiên bản kỳ vọng (optimistic locking).
     *
     * @param expected phiên bản kỳ vọng
     * @throws com.familya.platform.error.OptimisticConcurrencyException nếu lệch phiên bản
     */
    private void requireVersion(long expected) {
        if (this.version != expected) {
            throw new com.familya.platform.error.OptimisticConcurrencyException(
                    "Tree " + id + " version " + this.version + " != expected " + expected);
        }
    }

    /** Tăng số phiên bản để đánh dấu aggregate đã mutate. */
    private void bump() {
        this.version = this.version + 1;
    }

    /**
     * Tập trạng thái của cây.
     */
    public enum State {
        /** Cây đang hoạt động bình thường. */
        ACTIVE,
        /** Cây bị đóng băng (các thao tác ghi bị chặn). */
        FROZEN,
        /** Cây đã đánh dấu tombstone (không thể đảo ngược). */
        TOMBSTONED,
        /** Trạng thái đóng băng riêng cho Saga xóa. */
        DELETE_FROZEN,
        /** Trạng thái chờ xử lý trong Saga xóa. */
        PENDING_DELETION,
        /** Trạng thái đã xóa hoàn tất. */
        DELETION_FINALIZED;

        /**
         * @return {@code true} nếu trạng thái là ACTIVE
         */
        public boolean isActive()   { return this == ACTIVE; }
        /**
         * @return {@code true} nếu cây đang đóng băng (FROZEN hoặc DELETE_FROZEN)
         */
        public boolean isFrozen()   { return this == FROZEN || this == DELETE_FROZEN; }
        /**
         * @return {@code true} nếu cây đã tombstone (TOMBSTONED hoặc DELETION_FINALIZED)
         */
        public boolean isTombstoned(){ return this == TOMBSTONED || this == DELETION_FINALIZED; }
        /**
         * @return {@code true} nếu cây đang chờ xóa trong Saga
         */
        public boolean isPendingDeletion() { return this == PENDING_DELETION; }
    }
}