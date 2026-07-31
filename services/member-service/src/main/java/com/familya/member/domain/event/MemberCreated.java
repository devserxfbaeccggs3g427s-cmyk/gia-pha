package com.familya.member.domain.event;

import com.familya.member.domain.model.Member;

import java.time.Instant;
import java.util.UUID;

/**
 * Sự kiện thành viên được tạo hoặc cập nhật. Phát ra khi một thành viên được tạo mới
 * hoặc khi các trường profile được thay đổi (cùng topic {@code member.events.v1}).
 */
public final class MemberCreated extends MemberEvent {
    private final UUID treeId;
    private final UUID memberId;
    private final UUID userId;
    private final String displayName;
    private final Member.Gender gender;
    private final Member.Status status;
    private final long revision;
    private final long epoch;
    private final Instant occurredAt;

    /**
     * Khởi tạo sự kiện.
     *
     * @param treeId      mã cây
     * @param memberId    mã thành viên
     * @param userId      mã người dùng hệ thống (nếu có)
     * @param displayName tên hiển thị
     * @param gender      giới tính
     * @param status      trạng thái
     * @param revision    phiên bản aggregate
     * @param epoch       epoch
     * @param occurredAt  thời điểm xảy ra
     */
    public MemberCreated(UUID treeId, UUID memberId, UUID userId, String displayName,
                         Member.Gender gender, Member.Status status,
                         long revision, long epoch, Instant occurredAt) {
        this.treeId = treeId;
        this.memberId = memberId;
        this.userId = userId;
        this.displayName = displayName;
        this.gender = gender;
        this.status = status;
        this.revision = revision;
        this.epoch = epoch;
        this.occurredAt = occurredAt;
    }

    @Override public UUID treeId() { return treeId; }
    @Override public UUID memberId() { return memberId; }
    @Override public String eventType() { return "MemberCreated"; }
    @Override public int eventVersion() { return 1; }
    @Override public Instant occurredAt() { return occurredAt; }

    /** Lấy mã người dùng hệ thống (có thể null). */
    public UUID userId() { return userId; }
    /** Lấy tên hiển thị. */
    public String displayName() { return displayName; }
    /** Lấy giới tính. */
    public Member.Gender gender() { return gender; }
    /** Lấy trạng thái. */
    public Member.Status status() { return status; }
    /** Lấy phiên bản aggregate. */
    public long revision() { return revision; }
    /** Lấy epoch. */
    public long epoch() { return epoch; }
}