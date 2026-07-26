package vn.giapha.research.sharing.domain.model;

import java.time.Instant;
import java.util.Set;

/**
 * One share link row (Task 33). The token is never stored — only the
 * SHA-256 hash lives in {@code share_links.token_hash}; the raw token
 * rides the URL once and never re-appears.
 */
public record ShareLink(
        long shareLinkKey,
        String externalId,
        long treeKey,
        byte[] tokenHash,
        byte[] tokenNonce,
        SharePermission permission,
        Instant expiresAt,
        Instant revokedAt,
        Long createdByUserKey,
        Set<String> allowedScopes,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public ShareLink {
        allowedScopes = allowedScopes == null ? Set.of() : Set.copyOf(allowedScopes);
    }

    public boolean revoked() {
        return revokedAt != null;
    }

    public boolean expired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    public enum SharePermission {
        VIEW
    }
}
