package com.familya.relationship.application.port.out;

import java.util.Optional;
import java.util.UUID;

public interface AuthorizationProjectionRepository {

    Optional<RelationshipAuthRow> findAuth(UUID treeId, UUID userId);

    void upsertAuth(RelationshipAuthRow row);

    record RelationshipAuthRow(UUID treeId, UUID userId, String role,
                                long revision, long epoch,
                                java.time.Instant grantedAt, boolean revoked,
                                String sourceEventId, java.time.Instant lastUpdatedAt) {
        public boolean isRevoked() { return revoked || role == null; }
        public boolean canEdit() { return role != null && (role.equals("ADMIN") || role.equals("EDITOR")); }
    }
}