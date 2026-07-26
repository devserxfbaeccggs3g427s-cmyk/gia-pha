package vn.giapha.research.identity.domain.auth;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Final authenticated session record (Task 19.5). Opaque on the wire — the
 * cookie carries only the session {@code external_id}; the server resolves it
 * to this row and validates the token hash + expiry. The token hash column
 * (BINARY(32)) is what allows rotation: a new session id is issued, the
 * previous one is revoked atomically, and only the active row is verifiable.
 *
 * <p>{@link #scopes()} hold non-elevation authorities (tree, share) carried
 * over from the bridge token during cutover; {@link #idleExpiresAt()} and
 * {@link #absoluteExpiresAt()} implement the 30-minute idle and 24-hour
 * absolute policy (Req 2.3).
 */
public record AuthSession(
        long sessionKey,
        String externalId,
        long userKey,
        byte[] tokenHash,
        Instant issuedAt,
        Instant lastSeenAt,
        Instant idleExpiresAt,
        Instant absoluteExpiresAt,
        Long rotatedFromSessionKey,
        Instant revokedAt,
        String revokeReason,
        Set<String> scopes) {

    public boolean revoked() {
        return revokedAt != null;
    }

    public boolean idleExpired(Instant now) {
        return !idleExpiresAt.isAfter(now);
    }

    public boolean absoluteExpired(Instant now) {
        return !absoluteExpiresAt.isAfter(now);
    }

    public List<String> scopeList() {
        return List.copyOf(scopes);
    }
}
