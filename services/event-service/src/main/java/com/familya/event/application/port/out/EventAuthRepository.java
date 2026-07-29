package com.familya.event.application.port.out;

import java.util.Optional;
import java.util.UUID;

public interface EventAuthRepository {

    Optional<EventAuthRow> findAuth(UUID treeId, UUID userId);

    void upsertAuth(EventAuthRow row);

    record EventAuthRow(UUID treeId, UUID userId, String role,
                         long revision, long epoch,
                         java.time.Instant grantedAt, boolean revoked,
                         String sourceEventId, java.time.Instant lastUpdatedAt) {
        public boolean isRevoked() { return revoked || role == null; }
        public boolean canEdit() { return role != null && (role.equals("ADMIN") || role.equals("EDITOR")); }
    }
}