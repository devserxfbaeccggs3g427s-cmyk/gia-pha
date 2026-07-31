package com.familya.sharing.application.port.out;

import java.util.Optional;
import java.util.UUID;

public interface ShareAuthRepository {
    Optional<AuthRow> find(UUID treeId, UUID userId);

    void upsert(UUID treeId, UUID userId, String role, long revision, long epoch,
                java.time.Instant grantedAt, boolean revoked, String sourceEventId);

    record AuthRow(UUID treeId, UUID userId, String role, long revision, long epoch,
                   boolean revoked, java.time.Instant lastUpdatedAt) {
        public boolean isRevoked() { return revoked || role == null; }
        public boolean canShare() { return role != null && (role.equals("ADMIN") || role.equals("EDITOR")); }
    }
}
