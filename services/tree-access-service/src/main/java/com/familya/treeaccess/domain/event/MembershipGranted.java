package com.familya.treeaccess.domain.event;

import com.familya.treeaccess.domain.model.TreeMembership;

import java.time.Instant;
import java.util.UUID;

public final class MembershipGranted extends MembershipEvent {
    private final UUID treeId;
    private final UUID userId;
    private final TreeMembership.Role role;
    private final UUID grantedBy;
    private final Instant occurredAt;

    public MembershipGranted(UUID treeId, UUID userId, TreeMembership.Role role,
                             UUID grantedBy, Instant occurredAt) {
        this.treeId = treeId;
        this.userId = userId;
        this.role = role;
        this.grantedBy = grantedBy;
        this.occurredAt = occurredAt;
    }

    @Override public UUID treeId() { return treeId; }
    @Override public UUID userId() { return userId; }
    @Override public String eventType() { return "MembershipGranted"; }
    @Override public int eventVersion() { return 1; }
    @Override public Instant occurredAt() { return occurredAt; }

    public TreeMembership.Role role() { return role; }
    public UUID grantedBy() { return grantedBy; }
}