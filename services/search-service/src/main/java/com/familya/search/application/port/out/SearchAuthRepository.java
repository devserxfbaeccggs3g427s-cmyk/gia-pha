package com.familya.search.application.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Read-only authorization projection consulted by the search
 * use cases. The search service never issues authoritative
 * permission decisions; it only checks whether the caller's
 * authorization projection row is present and not revoked, and
 * reports the observed revision alongside the search result.
 */
public interface SearchAuthRepository {

    Optional<AuthRow> find(UUID treeId, UUID userId);

    record AuthRow(UUID treeId, UUID userId, String role, long revision,
                   boolean revoked, java.time.Instant lastUpdatedAt) {
        public boolean isRevoked() { return revoked || role == null; }
    }
}
