package vn.giapha.research.identity.domain.model;

import java.time.Instant;

/**
 * Row from {@code verification_tokens} (Task 17.1). Only the SHA-256 hash of
 * the raw token is ever persisted; consumption is a single-statement
 * compare-and-set so a token can be redeemed exactly once.
 */
public record VerificationToken(
        long verificationTokenKey,
        long userKey,
        String purpose,
        byte[] tokenHash,
        Instant expiresAt,
        Instant consumedAt,
        Instant createdAt) {

    public static final String PURPOSE_EMAIL_VERIFICATION = "EMAIL_VERIFICATION";

    public boolean usable(Instant now) {
        return consumedAt == null && expiresAt.isAfter(now);
    }
}
