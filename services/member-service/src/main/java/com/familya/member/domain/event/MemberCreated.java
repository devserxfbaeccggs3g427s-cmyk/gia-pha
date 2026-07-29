package com.familya.member.domain.event;

import com.familya.member.domain.model.Member;

import java.time.Instant;
import java.util.UUID;

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

    public UUID userId() { return userId; }
    public String displayName() { return displayName; }
    public Member.Gender gender() { return gender; }
    public Member.Status status() { return status; }
    public long revision() { return revision; }
    public long epoch() { return epoch; }
}