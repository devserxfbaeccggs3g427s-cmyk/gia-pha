package com.familya.member.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Sự kiện thành viên bị tombstone. Phát ra khi một thành viên được đánh dấu xóa mềm,
 * cho phép các projection downstream cập nhật trạng thái.
 */
public final class MemberTombstoned extends MemberEvent {
    private final UUID treeId;
    private final UUID memberId;
    private final long revision;
    private final long epoch;
    private final Instant occurredAt;

    /**
     * Khởi tạo sự kiện tombstone.
     *
     * @param treeId     mã cây
     * @param memberId   mã thành viên
     * @param revision   phiên bản aggregate
     * @param epoch      epoch
     * @param occurredAt thời điểm xảy ra
     */
    public MemberTombstoned(UUID treeId, UUID memberId, long revision, long epoch, Instant occurredAt) {
        this.treeId = treeId;
        this.memberId = memberId;
        this.revision = revision;
        this.epoch = epoch;
        this.occurredAt = occurredAt;
    }

    @Override public UUID treeId() { return treeId; }
    @Override public UUID memberId() { return memberId; }
    @Override public String eventType() { return "MemberTombstoned"; }
    @Override public int eventVersion() { return 1; }
    @Override public Instant occurredAt() { return occurredAt; }

    /** Phiên bản aggregate. */
    public long revision() { return revision; }
    /** Epoch. */
    public long epoch() { return epoch; }
}