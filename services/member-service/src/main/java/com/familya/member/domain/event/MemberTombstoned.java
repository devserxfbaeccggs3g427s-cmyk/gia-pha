package com.familya.member.domain.event;

import java.time.Instant;
import java.util.UUID;

public final class MemberTombstoned extends MemberEvent {
    private final UUID treeId;
    private final UUID memberId;
    private final long revision;
    private final long epoch;
    private final Instant occurredAt;

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

    public long revision() { return revision; }
    public long epoch() { return epoch; }
}