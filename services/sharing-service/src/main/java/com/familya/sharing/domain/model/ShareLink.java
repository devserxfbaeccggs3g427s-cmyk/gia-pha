package com.familya.sharing.domain.model;

import java.time.Instant;
import java.util.UUID;

public record ShareLink(
        UUID id,
        UUID treeId,
        Scope scope,
        UUID targetId,
        Role role,
        String tokenHash,
        UUID createdByUserId,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt,
        String revocationReason,
        long revision,
        long version
) {
    public enum Scope { TREE, MEMBER, MEDIA, EVENT }
    public enum Role { VIEWER, CONTRIBUTOR, EDITOR, ADMIN }
}
