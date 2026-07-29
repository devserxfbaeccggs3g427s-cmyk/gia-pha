package com.familya.treeaccess.domain.event;

import java.time.Instant;
import java.util.UUID;

public final class MembershipRevoked extends MembershipEvent {
    private final UUID treeId;
    private final UUID userId;
    private final UUID revokedBy;
    private final String reason;
    private final Instant occurredAt;

    public MembershipRevoked(UUID treeId, UUID userId, UUID revokedBy, String reason, Instant occurredAt) {
        this.treeId = treeId;
        this.userId = userId;
        this.revokedBy = revokedBy;
        this.reason = reason;
        this.occurredAt = occurredAt;
    }

    @Override public UUID treeId() { return treeId; }
    @Override public UUID userId() { return userId; }
    @Override public String eventType() { return "MembershipRevoked"; }
    @Override public int eventVersion() { return 1; }
    @Override public Instant occurredAt() { return occurredAt; }

    public UUID revokedBy() { return revokedBy; }
    public String reason() { return reason; }
}