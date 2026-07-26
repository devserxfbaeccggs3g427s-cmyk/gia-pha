package vn.giapha.research.identity.domain.model;

import java.time.Instant;

/**
 * User aggregate row from {@code users} (Task 17.1).
 *
 * <p>{@code externalId} keeps the unchanged legacy nanoid so every historical
 * reference (tree ownership, memberships, audit records) stays valid.
 * {@code passwordHash} is {@code null} for OAuth-only accounts — the legacy
 * store used an empty string, which the migration normalizes to {@code null}.
 */
public record User(
        long userKey,
        String externalId,
        String email,
        String name,
        String passwordHash,
        String imageUrl,
        AuthProvider provider,
        Instant emailVerifiedAt,
        int failedLoginAttempts,
        Instant lockedUntil,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt) {

    public boolean hasPassword() {
        return passwordHash != null && !passwordHash.isEmpty();
    }

    public boolean emailVerified() {
        return emailVerifiedAt != null;
    }
}
