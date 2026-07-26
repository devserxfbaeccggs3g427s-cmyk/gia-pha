package vn.giapha.research.identity.domain.model;

import java.time.Instant;
import java.util.Locale;

import vn.giapha.research.identity.infrastructure.kernel.error.ValidationException;

/**
 * Insert payload for a {@code users} row (Task 17.1). Used by registration
 * (Task 19.1), the NextAuth-parity OAuth flow and the users.json migration —
 * all three funnel through the same normalization so the
 * {@code ck_users_email_normalized} constraint can never trip at runtime.
 */
public record NewUser(
        String externalId,
        String email,
        String name,
        String passwordHash,
        String imageUrl,
        AuthProvider provider,
        Instant emailVerifiedAt,
        int failedLoginAttempts,
        Instant lockedUntil,
        Instant createdAt,
        Instant updatedAt) {

    public NewUser {
        email = normalizeEmail(email);
        // Legacy OAuth adapter stored passwordHash: '' — normalize to NULL.
        if (passwordHash != null && passwordHash.isEmpty()) {
            passwordHash = null;
        }
    }

    /**
     * Frozen legacy normalization ({@code user-store.ts#normalizeEmail}):
     * trim, then lowercase. Locale-invariant so Turkish-I style surprises
     * cannot produce a different key than MySQL's own {@code LOWER()}.
     */
    public static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ValidationException("Email is required");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
