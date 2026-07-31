package com.familya.treeaccess.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Migration loader input. Each tree manifest carries immutable IDs and
 * timestamps; the loader is idempotent per treeId. Used by the
 * Migration Service during the legacy cutover.
 */
public record LoadTreeManifestCommand(
        UUID treeId,
        UUID ownerUserId,
        String name,
        long initialRevision,
        long initialEpoch,
        Instant createdAt,
        List<MembershipLine> memberships,
        boolean replaySafe
) {
    public record MembershipLine(UUID userId, String role, UUID grantedBy, Instant grantedAt) { }
}