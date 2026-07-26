package vn.giapha.research.identity.application.service;

import java.time.Instant;
import java.util.List;

import vn.giapha.research.identity.domain.model.AuthProvider;
import vn.giapha.research.identity.infrastructure.kernel.crypto.Hashes;

/**
 * Canonical identity fingerprint (Task 17.3). Source (users.json) and target
 * (MySQL) are both reduced to the same canonical text — one record per user,
 * sorted by external ID — and hashed. Equal digests prove the accepted
 * user/OAuth counts, password hashes, verification and lockout state
 * reconcile exactly (Task 17 DoD) without ever comparing PII by eye.
 *
 * <p>Instants are canonicalized to epoch microseconds (DATETIME(6) precision)
 * so source millisecond timestamps and database round-trips agree.
 */
public final class IdentityDigest {

    private static final char FIELD_SEP = '\u001F';
    private static final char RECORD_SEP = '\u001E';
    private static final String NULL_MARK = "-";

    private IdentityDigest() {}

    /** One canonical record; {@code oauthKeys} must already be sorted. */
    public static String canonicalLine(String externalId, String email, String name,
            String passwordHash, AuthProvider provider, Instant emailVerifiedAt,
            int failedLoginAttempts, Instant lockedUntil, Instant createdAt,
            List<String> oauthKeys) {
        return canonicalLine(externalId, email, name, passwordHash, provider, emailVerifiedAt,
                failedLoginAttempts, lockedUntil, createdAt, oauthKeys, "-");
    }

    /**
     * One canonical record with the outstanding verification-token digest
     * appended. {@code tokenDigest} is the joined, sorted list of
     * {@link #tokenDigest} entries (or {@code "-"} when none exist).
     */
    public static String canonicalLine(String externalId, String email, String name,
            String passwordHash, AuthProvider provider, Instant emailVerifiedAt,
            int failedLoginAttempts, Instant lockedUntil, Instant createdAt,
            List<String> oauthKeys, String tokenDigest) {
        StringBuilder line = new StringBuilder();
        line.append(externalId).append(FIELD_SEP)
                .append(email).append(FIELD_SEP)
                .append(name).append(FIELD_SEP)
                .append(passwordHash == null ? NULL_MARK : passwordHash).append(FIELD_SEP)
                .append(provider.dbValue()).append(FIELD_SEP)
                .append(micros(emailVerifiedAt)).append(FIELD_SEP)
                .append(failedLoginAttempts).append(FIELD_SEP)
                .append(micros(lockedUntil)).append(FIELD_SEP)
                .append(micros(createdAt)).append(FIELD_SEP)
                .append(String.join(",", oauthKeys)).append(FIELD_SEP)
                .append(tokenDigest == null || tokenDigest.isEmpty() ? NULL_MARK : tokenDigest);
        return line.toString();
    }

    /**
     * Compact fingerprint of an outstanding verification token: SHA-256 hex
     * prefix + canonicalized expiry microsecond. Deterministic across source
     * and target so reconciliation can compare without exposing the raw hash.
     */
    public static String tokenDigest(byte[] tokenHash, Instant expiresAt) {
        StringBuilder hex = new StringBuilder();
        for (byte b : tokenHash) {
            hex.append(String.format("%02x", b & 0xff));
        }
        return hex.toString().substring(0, Math.min(16, hex.length())) + ":"
                + micros(expiresAt);
    }

    public static String oauthKey(AuthProvider provider, String providerAccountId) {
        return provider.dbValue() + ":" + providerAccountId;
    }

    /** SHA-256 hex over the record-separated canonical lines (already sorted). */
    public static String digestHex(List<String> canonicalLines) {
        String joined = String.join(String.valueOf(RECORD_SEP), canonicalLines);
        return Hashes.hex(Hashes.sha256(joined));
    }

    private static String micros(Instant instant) {
        if (instant == null) {
            return NULL_MARK;
        }
        return String.valueOf(
                Math.addExact(Math.multiplyExact(instant.getEpochSecond(), 1_000_000L),
                        instant.getNano() / 1_000L));
    }
}
