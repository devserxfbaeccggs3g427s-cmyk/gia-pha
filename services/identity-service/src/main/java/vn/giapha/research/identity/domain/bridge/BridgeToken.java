package vn.giapha.research.identity.domain.bridge;

import java.time.Instant;
import java.util.Set;

/**
 * A verified NextAuth bridge JWT — the payload the Spring security layer
 * trusts after {@link vn.giapha.research.identity.application.bridge.BridgeTokenVerifier}
 * has accepted the token (Task 18). Every claim is normalized here so call
 * sites never touch raw JWT maps.
 *
 * <p>Pinned claims (Task 18.3, Req 2.7):
 * <ul>
 *   <li>{@code iss} — issuer, exact match against the configured
 *       {@code giapha.auth.bridge-issuer};</li>
 *   <li>{@code aud} — audience, exact match against
 *       {@code giapha.auth.bridge-audience};</li>
 *   <li>{@code alg} — asymmetric ES256 (P-256 + SHA-256);</li>
 *   <li>{@code sub} — stable NextAuth user id;</li>
 *   <li>{@code iat} / {@code exp} — at most {@code bridgeMaxLifetime} apart
 *       (default: 5 minutes);</li>
 *   <li>{@code jti} — single-use identifier; replay is denied.</li>
 * </ul>
 *
 * <p>{@link #scopes()} are derived from the {@code scope} claim and limited
 * to well-known values so a malformed token cannot smuggle an arbitrary
 * authority.
 */
public record BridgeToken(
        String tokenId,
        String userExternalId,
        String issuer,
        String audience,
        String keyId,
        Instant issuedAt,
        Instant expiresAt,
        BridgeAuthStrength strength,
        Set<String> scopes) {

    public boolean expired(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
