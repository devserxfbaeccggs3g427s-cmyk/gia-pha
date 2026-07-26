package vn.giapha.research.identity.application.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.giapha.research.identity.application.port.out.AuthSessionRepository;
import vn.giapha.research.identity.domain.auth.AuthSession;
import vn.giapha.research.identity.domain.model.User;
import vn.giapha.research.identity.infrastructure.kernel.crypto.Hashes;

/**
 * Final Spring authentication sessions (Task 19.5, Req 2.11).
 *
 * <p>Opaque, server-side sessions are issued on successful credential or
 * OAuth login. The cookie carries an opaque {@code external_id}; the
 * server stores only the SHA-256 hash of the matching token. Rotation
 * revokes the previous row inside the same transaction as the new insert.
 *
 * <p>Idle timeout: 30 minutes (Req 2.3). Absolute timeout: 24 hours. CSRF
 * is handled by the {@code CookieCsrfTokenRepository} in
 * {@code SecurityConfig}; bearer-authenticated requests bypass CSRF because
 * cross-site attackers cannot attach a custom header.
 */
@Service
public class AuthSessionService {

    /** Frozen legacy idle timeout (Req 2.3). */
    public static final Duration IDLE_TIMEOUT = Duration.ofMinutes(30);

    /** Frozen legacy absolute timeout. */
    public static final Duration ABSOLUTE_TIMEOUT = Duration.ofHours(24);

    private static final int TOKEN_BYTES = 48;
    private final SecureRandom random = new SecureRandom();

    private final AuthSessionRepository sessions;

    public AuthSessionService(AuthSessionRepository sessions) {
        this.sessions = sessions;
    }

    /**
     * Issue a fresh session for the supplied user. The returned
     * {@link IssuedSession} carries the opaque cookie value, its expiry and
     * the resolved {@link AuthSession} row. {@code scopes} is an optional set
     * of authority scopes carried over from the bridge token during cutover.
     */
    @Transactional
    public IssuedSession issue(User user, Set<String> scopes, Instant now) {
        if (user == null) {
            throw new IllegalArgumentException("user is required");
        }
        String externalId = randomUrlSafeId();
        byte[] token = randomToken();
        byte[] tokenHash = Hashes.sha256(token);
        Instant idle = now.plus(IDLE_TIMEOUT);
        Instant absolute = now.plus(ABSOLUTE_TIMEOUT);
        AuthSession session = new AuthSession(0, externalId, user.userKey(), tokenHash,
                now, now, idle, absolute, null, null, null,
                scopes == null ? Set.of() : Set.copyOf(scopes));
        long sessionKey = sessions.insert(session);
        return new IssuedSession(
                externalId,
                encodeCookieValue(externalId, token),
                now,
                idle,
                absolute,
                new AuthSession(sessionKey, externalId, user.userKey(), tokenHash,
                        now, now, idle, absolute, null, null, null,
                        scopes == null ? Set.of() : Set.copyOf(scopes)));
    }

    /**
     * Rotate a session: insert a new row, revoke the previous one inside the
     * same transaction. Returns the new {@link IssuedSession} whose cookie
     * value replaces the previous one on the next response.
     */
    @Transactional
    public IssuedSession rotate(AuthSession previous, Set<String> scopes, Instant now) {
        if (previous == null) {
            throw new IllegalArgumentException("previous session is required");
        }
        sessions.revoke(previous.sessionKey(), now, "ROTATED");
        Set<String> effectiveScopes = scopes == null ? previous.scopes() : scopes;
        String externalId = randomUrlSafeId();
        byte[] token = randomToken();
        byte[] tokenHash = Hashes.sha256(token);
        Instant idle = now.plus(IDLE_TIMEOUT);
        Instant absolute = previous.absoluteExpiresAt();
        AuthSession row = new AuthSession(0, externalId, previous.userKey(), tokenHash,
                now, now, idle, absolute, previous.sessionKey(), null, null,
                Set.copyOf(effectiveScopes));
        long sessionKey = sessions.insert(row);
        return new IssuedSession(externalId, encodeCookieValue(externalId, token),
                now, idle, absolute,
                new AuthSession(sessionKey, externalId, previous.userKey(), tokenHash,
                        now, now, idle, absolute, previous.sessionKey(), null, null,
                        Set.copyOf(effectiveScopes)));
    }

    /**
     * Resolve a cookie value back to a verified session. The supplied
     * {@code now} is used for both idle and absolute expiry checks; the
     * token hash must match the row's {@code token_hash} exactly.
     */
    public Optional<AuthSession> resolve(String cookieValue, Instant now) {
        Decoded decoded = decodeCookieValue(cookieValue);
        if (decoded == null) {
            return Optional.empty();
        }
        Optional<AuthSession> session = sessions.findByExternalId(decoded.externalId);
        if (session.isEmpty()) {
            return Optional.empty();
        }
        AuthSession row = session.get();
        if (row.revoked() || row.idleExpired(now) || row.absoluteExpired(now)) {
            return Optional.empty();
        }
        if (!constantTimeEquals(row.tokenHash(), Hashes.sha256(decoded.token))) {
            return Optional.empty();
        }
        return Optional.of(row);
    }

    @Transactional
    public boolean touch(AuthSession session, Instant now) {
        Instant nextIdle = now.plus(IDLE_TIMEOUT);
        return sessions.touch(session.sessionKey(), now, nextIdle);
    }

    @Transactional
    public boolean revoke(AuthSession session, String reason, Instant now) {
        return sessions.revoke(session.sessionKey(), now, reason);
    }

    @Transactional
    public int revokeAllForUser(long userKey, String reason, Instant now) {
        return sessions.revokeAllForUser(userKey, now, reason);
    }

    /* --- Cookie encoding ------------------------------------------------ */

    private static String encodeCookieValue(String externalId, byte[] token) {
        return externalId + "." + base64Url(token);
    }

    private static Decoded decodeCookieValue(String cookieValue) {
        if (cookieValue == null || cookieValue.isBlank()) {
            return null;
        }
        int dot = cookieValue.indexOf('.');
        if (dot <= 0 || dot == cookieValue.length() - 1) {
            return null;
        }
        String externalId = cookieValue.substring(0, dot);
        byte[] token;
        try {
            token = Base64.getUrlDecoder().decode(cookieValue.substring(dot + 1));
        } catch (IllegalArgumentException malformed) {
            return null;
        }
        if (token.length != TOKEN_BYTES) {
            return null;
        }
        return new Decoded(externalId, token);
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] randomToken() {
        byte[] token = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(token);
        return token;
    }

    private static String randomUrlSafeId() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return base64Url(bytes);
    }

    private static boolean constantTimeEquals(byte[] left, byte[] right) {
        if (left == null || right == null || left.length != right.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < left.length; i++) {
            diff |= left[i] ^ right[i];
        }
        return diff == 0;
    }

    public record IssuedSession(String externalId, String cookieValue, Instant issuedAt,
            Instant idleExpiresAt, Instant absoluteExpiresAt, AuthSession row) {

        public String cookieName() {
            return "gp-session";
        }

        public String cookieAttributes(boolean secure) {
            int maxAge = (int) Math.max(0, idleExpiresAt.getEpochSecond()
                    - Instant.now().getEpochSecond());
            StringBuilder sb = new StringBuilder();
            sb.append(cookieName()).append('=').append(cookieValue)
                    .append("; Path=/; HttpOnly; SameSite=Lax; Max-Age=").append(maxAge);
            if (secure) {
                sb.append("; Secure");
            }
            return sb.toString();
        }
    }

    private record Decoded(String externalId, byte[] token) {}
}
