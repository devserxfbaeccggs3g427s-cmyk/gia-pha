package vn.giapha.research.identity.application.bridge;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import vn.giapha.research.identity.domain.bridge.BridgeAuthStrength;
import vn.giapha.research.identity.domain.bridge.BridgeToken;

/**
 * NextAuth bridge JWT issuer and verifier (Task 18, Req 2.5-2.7, ADR-009).
 *
 * <p>Issuance path:
 * <ol>
 *   <li>Next.js has already validated the encrypted NextAuth session cookie
 *       (server-side, via the {@code next-auth/jwt} decrypt path);</li>
 *   <li>the request handler hands us the resolved user id plus the optional
 *       step-up strength;</li>
 *   <li>we mint a short-lived ES256 JWT bound to {@code iss}, {@code aud},
 *       {@code sub}, {@code iat}, {@code exp}, {@code jti} and the
 *       {@code str} claim;</li>
 *   <li>we add the new {@code jti} to the {@link BridgeReplayStore} so the
 *       very first verification rejects a single-use replay.</li>
 * </ol>
 *
 * <p>The bridge token lives at most {@link #maxLifetime} (default 5 minutes
 * — Req 2.5 mandates "tối đa năm phút"). The browser must keep it in
 * memory only; the Spring API never echoes it to logs (Req 2.6, 15.9).
 *
 * <p>The {@link #killSwitch} is a runtime flag the operator can flip without
 * a deployment to refuse new issuances and verifications (Task 18.4).
 */
public class BridgeTokenService {

    /** Frozen bridge max-lifetime (Req 2.5: "tối đa năm phút"). */
    public static final Duration MAX_LIFETIME = Duration.ofMinutes(5);

    /** Frozen lower bound: a token younger than 5 seconds is replay-prone. */
    public static final Duration MIN_LIFETIME = Duration.ofSeconds(30);

    private final BridgeKeyRegistry keys;
    private final BridgeReplayStore replayStore;
    private final String issuer;
    private final String audience;
    private final AtomicBoolean killSwitch = new AtomicBoolean(false);

    private volatile Duration maxLifetime = MAX_LIFETIME;
    private volatile String activeKeyId;

    public BridgeTokenService(BridgeKeyRegistry keys, BridgeReplayStore replayStore,
            String issuer, String audience) {
        this.keys = keys;
        this.replayStore = replayStore;
        this.issuer = requireNonBlank(issuer, "issuer");
        this.audience = requireNonBlank(audience, "audience");
    }

    /* --- Configuration overrides --------------------------------------- */

    public void overrideMaxLifetime(Duration maxLifetime) {
        if (maxLifetime == null || maxLifetime.compareTo(MIN_LIFETIME) < 0
                || maxLifetime.compareTo(MAX_LIFETIME) > 0) {
            throw new IllegalArgumentException(
                    "Bridge max-lifetime must be within " + MIN_LIFETIME + ".." + MAX_LIFETIME);
        }
        this.maxLifetime = maxLifetime;
    }

    public Duration maxLifetime() {
        return maxLifetime;
    }

    public void overrideActiveKey(String keyId) {
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("keyId is required");
        }
        if (!keys.hasSigningKey(keyId)) {
            throw new IllegalArgumentException("Unknown active signing key: " + keyId);
        }
        this.activeKeyId = keyId;
    }

    public void killSwitch(boolean active) {
        this.killSwitch.set(active);
    }

    public boolean isKilled() {
        return killSwitch.get();
    }

    public String activeSigningKeyId() {
        return activeKeyId != null ? activeKeyId : keys.activeSigningKeyId();
    }

    /* --- Issuance ------------------------------------------------------ */

    /**
     * Mint a bridge JWT for the supplied {@code userExternalId}. {@code strength}
     * controls the step-up claim; {@code scopes} is optional and defaults to
     * the empty set. The caller must already have validated the underlying
     * NextAuth session (Task 18.1).
     */
    public IssuedBridgeToken issue(String userExternalId, BridgeAuthStrength strength,
            List<String> scopes) {
        if (killSwitch.get()) {
            throw new BridgeKilledException("Bridge is disabled");
        }
        String resolvedKeyId = activeKeyId != null ? activeKeyId : keys.activeSigningKeyId();
        PrivateKey privateKey = keys.signingKey(resolvedKeyId);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(maxLifetime);
        String tokenId = UUID.randomUUID().toString();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", issuer);
        claims.put("aud", audience);
        claims.put("sub", requireNonBlank(userExternalId, "userExternalId"));
        claims.put("iat", now.getEpochSecond());
        claims.put("nbf", now.getEpochSecond());
        claims.put("exp", expiresAt.getEpochSecond());
        claims.put("jti", tokenId);
        claims.put("str", strength == null ? BridgeAuthStrength.SESSION.name() : strength.name());
        if (scopes != null && !scopes.isEmpty()) {
            claims.put("scope", String.join(" ", scopes));
        }
        replayStore.register(tokenId, expiresAt);
        String payload = serialize(claims);
        String compact = BridgeEs256.sign(privateKey, resolvedKeyId, payload);
        return new IssuedBridgeToken(compact, tokenId, expiresAt, strength);
    }

    /* --- Verification -------------------------------------------------- */

    /**
     * Verify a compact bridge JWT and return the parsed {@link BridgeToken}.
     * Throws {@link BridgeVerificationException} on any failure — never a
     * detailed message leaks past this method.
     */
    public BridgeToken verify(String compact) {
        if (killSwitch.get()) {
            throw new BridgeVerificationException("Bridge is disabled");
        }
        if (compact == null || compact.isBlank()) {
            throw new BridgeVerificationException("Empty token");
        }
        String[] parts = compact.split("\\.");
        if (parts.length != 3) {
            throw new BridgeVerificationException("JWT must have three segments");
        }
        String headerJson;
        try {
            headerJson = new String(BridgeEs256.base64UrlDecode(parts[0]),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (BridgeVerificationException decodeFailed) {
            throw decodeFailed;
        }
        JsonMap header = JsonMap.parse(headerJson);
        if (!BridgeEs256.ALG.equals(header.requireString("alg"))) {
            throw new BridgeVerificationException("Unsupported alg");
        }
        String kid = header.string("kid");
        if (kid == null) {
            throw new BridgeVerificationException("Missing kid");
        }
        PublicKey verificationKey = keys.verificationKey(kid);
        BridgeEs256.Decoded decoded = BridgeEs256.verify(verificationKey, kid, compact);
        JsonMap claims = JsonMap.parse(new String(decoded.payload(),
                java.nio.charset.StandardCharsets.UTF_8));

        String iss = claims.requireString("iss");
        String aud = claims.requireString("aud");
        String sub = claims.requireString("sub");
        long iat = claims.longValue("iat");
        long exp = claims.longValue("exp");
        String jti = claims.requireString("jti");
        String str = claims.string("str");
        List<String> scopes = claims.stringList("scope");
        List<String> scopeList = scopes.isEmpty() ? List.of()
                : Arrays.asList(scopes.get(0).split(" "));
        Instant now = Instant.now();
        Instant issuedAt = Instant.ofEpochSecond(iat);
        Instant expiresAt = Instant.ofEpochSecond(exp);

        if (!issuer.equals(iss)) {
            throw new BridgeVerificationException("Issuer mismatch");
        }
        if (!audience.equals(aud)) {
            throw new BridgeVerificationException("Audience mismatch");
        }
        if (expiresAt.isBefore(now) || expiresAt.isAfter(now.plus(maxLifetime.plus(Duration.ofSeconds(5))))) {
            // Reject expired tokens and tokens whose lifetime exceeds the
            // configured maximum (a 5-second slack accommodates clock skew).
            throw new BridgeVerificationException("Invalid exp");
        }
        if (issuedAt.isAfter(now.plus(Duration.ofSeconds(30)))) {
            throw new BridgeVerificationException("Token issued in the future");
        }
        if (!replayStore.consume(jti, expiresAt)) {
            throw new BridgeVerificationException("Replay detected");
        }
        BridgeAuthStrength strength = parseStrength(str);
        return new BridgeToken(jti, sub, iss, aud, kid, issuedAt, expiresAt, strength,
                java.util.Set.copyOf(scopeList));
    }

    public String issuer() {
        return issuer;
    }

    public String audience() {
        return audience;
    }

    /* --- helpers ------------------------------------------------------- */

    private static BridgeAuthStrength parseStrength(String raw) {
        if (raw == null) {
            return BridgeAuthStrength.SESSION;
        }
        try {
            return BridgeAuthStrength.valueOf(raw);
        } catch (IllegalArgumentException unknown) {
            return BridgeAuthStrength.SESSION;
        }
    }

    private static String serialize(Map<String, Object> claims) {
        StringBuilder builder = new StringBuilder();
        builder.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> entry : claims.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('"').append(escape(entry.getKey())).append("\":");
            Object value = entry.getValue();
            if (value instanceof String s) {
                builder.append('"').append(escape(s)).append('"');
            } else if (value instanceof Number n) {
                builder.append(n.toString());
            } else if (value instanceof Boolean b) {
                builder.append(b.booleanValue() ? "true" : "false");
            } else {
                throw new IllegalArgumentException("Unsupported claim type: " + value.getClass());
            }
        }
        builder.append('}');
        return builder.toString();
    }

    private static String escape(String input) {
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    public record IssuedBridgeToken(String compact, String tokenId, Instant expiresAt,
            BridgeAuthStrength strength) {}
}
