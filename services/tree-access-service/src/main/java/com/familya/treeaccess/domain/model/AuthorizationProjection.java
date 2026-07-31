package com.familya.treeaccess.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Authorization projection row. The local copy in Tree Access Service
 * is authoritative; other services consume events into their own local
 * copies. Carries the revision/epoch so consumers can compare against
 * the tree's current revision and detect staleness.
 */
public record AuthorizationProjection(
        UUID treeId,
        UUID userId,
        TreeMembership.Role role,
        long revision,
        long epoch,
        Instant grantedAt,
        boolean revoked,
        String sourceEventId,
        Instant lastUpdatedAt
) {
    public boolean canEdit() { return role != null && role.canEdit(); }
    public boolean canView() { return role != null && role.canView(); }
}