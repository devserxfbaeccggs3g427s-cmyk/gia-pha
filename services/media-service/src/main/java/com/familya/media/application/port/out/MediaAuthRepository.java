package com.familya.media.application.port.out;

import java.util.Optional;
import java.util.UUID;

public interface MediaAuthRepository {

    Optional<MediaAuthRow> findAuth(UUID treeId, UUID userId);

    void upsertAuth(MediaAuthRow row);

    record MediaAuthRow(UUID treeId, UUID userId, String role,
                        long revision, long epoch,
                        java.time.Instant grantedAt, boolean revoked,
                        String sourceEventId, java.time.Instant lastUpdatedAt) {
        public boolean isRevoked() { return revoked || role == null; }
        public boolean canEdit() { return role != null && (role.equals("ADMIN") || role.equals("EDITOR")); }
    }
}
