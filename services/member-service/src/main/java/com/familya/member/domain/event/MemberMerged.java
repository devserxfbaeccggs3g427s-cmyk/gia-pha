package com.familya.member.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Internal merge event. The Member service emits this when two
 * members in the same tree are merged; downstream services replace
 * references to {@code sourceMemberId} with {@code targetMemberId}
 * using their projection.
 */
public final class MemberMerged extends MemberEvent {
    private final UUID treeId;
    private final UUID memberId;
    private final UUID sourceMemberId;
    private final long revision;
    private final long epoch;
    private final Instant occurredAt;

    /**
     * Khởi tạo sự kiện gộp thành viên.
     *
     * @param treeId         mã cây
     * @param memberId       mã thành viên survivor
     * @param sourceMemberId mã thành viên nguồn đã bị gộp
     * @param revision       phiên bản aggregate
     * @param epoch          epoch
     * @param occurredAt     thời điểm xảy ra
     */
    public MemberMerged(UUID treeId, UUID memberId, UUID sourceMemberId,
                        long revision, long epoch, Instant occurredAt) {
        this.treeId = treeId;
        this.memberId = memberId;
        this.sourceMemberId = sourceMemberId;
        this.revision = revision;
        this.epoch = epoch;
        this.occurredAt = occurredAt;
    }

    @Override public UUID treeId() { return treeId; }
    @Override public UUID memberId() { return memberId; }
    @Override public String eventType() { return "MemberMerged"; }
    @Override public int eventVersion() { return 1; }
    @Override public Instant occurredAt() { return occurredAt; }

    /** Mã thành viên nguồn đã bị gộp. */
    public UUID sourceMemberId() { return sourceMemberId; }
    /** Lấy phiên bản aggregate. */
    public long revision() { return revision; }
    /** Lấy epoch. */
    public long epoch() { return epoch; }
}