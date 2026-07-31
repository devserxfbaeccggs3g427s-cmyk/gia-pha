package com.familya.platform.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * NextAuth bridge token issuer. The bridge token is asymmetric
 * (RS256), audience-bound to the target service, and has a maximum
 * lifetime of 5 minutes (ADR-005). The public key is published to
 * the platform JWKS endpoint.
 */
public class BridgeTokenIssuer {

    private final RSAKey signingKey;
    private final String audience;
    private final long maxLifetimeSeconds;

    public BridgeTokenIssuer(String audience, long maxLifetimeSeconds) throws Exception {
        this.signingKey = new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
        this.audience = audience;
        this.maxLifetimeSeconds = Math.min(maxLifetimeSeconds, 300L);
    }

    public String issue(String subject, String email) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .audience(audience)
                .issuer("familya-nextauth-bridge")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(maxLifetimeSeconds)))
                .claim("email", email)
                .jwtID(UUID.randomUUID().toString())
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }

    private static final long ALLOWED_CLOCK_SKEW_SECONDS = 30;
    private static final String EXPECTED_ISSUER = "familya-nextauth-bridge";

    public boolean verify(String token) throws Exception {
        SignedJWT jwt = SignedJWT.parse(token);
        if (!jwt.verify(new RSASSAVerifier(signingKey.toRSAPublicKey()))) {
            return false;
        }
        JWTClaimsSet claims = jwt.getJWTClaimsSet();
        if (claims == null) {
            return false;
        }
        if (!EXPECTED_ISSUER.equals(claims.getIssuer())) {
            return false;
        }
        if (claims.getAudience() == null || !claims.getAudience().contains(audience)) {
            return false;
        }
        Instant now = Instant.now();
        Date expiration = claims.getExpirationTime();
        if (expiration == null || expiration.toInstant().isBefore(now.minusSeconds(ALLOWED_CLOCK_SKEW_SECONDS))) {
            return false;
        }
        Date notBefore = claims.getNotBeforeTime();
        if (notBefore != null && notBefore.toInstant().isAfter(now.plusSeconds(ALLOWED_CLOCK_SKEW_SECONDS))) {
            return false;
        }
        if (claims.getSubject() == null || claims.getSubject().isBlank()) {
            return false;
        }
        return true;
    }

    public RSAKey publicJwk() {
        return signingKey.toPublicJWK();
    }

    public String keyId() {
        return signingKey.getKeyID();
    }
}
