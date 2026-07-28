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

    public UUID sourceMemberId() { return sourceMemberId; }
    public long revision() { return revision; }
    public long epoch() { return epoch; }
}