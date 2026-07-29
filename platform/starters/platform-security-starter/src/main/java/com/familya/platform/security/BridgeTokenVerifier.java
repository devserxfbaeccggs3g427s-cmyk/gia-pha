package com.familya.platform.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

import java.net.URI;
import java.util.Set;

/**
 * Verifies NextAuth bridge tokens against the platform JWKS endpoint.
 *
 * <p>Unlike {@link BridgeTokenIssuer}, which is only meant to be used by the
 * token issuer itself, this class never generates its own signing key: it
 * fetches (and caches) the NextAuth bridge issuer's public keys from
 * {@code jwksUri} so that only tokens actually signed by the real bridge
 * issuer are accepted. This closes the self-signed no-op verification hole
 * where a service accepted only tokens it had signed itself.
 */
public class BridgeTokenVerifier {

    private static final String EXPECTED_ISSUER = "familya-nextauth-bridge";

    private final DefaultJWTProcessor<SecurityContext> processor;

    public BridgeTokenVerifier(String jwksUri, String audience) throws Exception {
        JWKSource<SecurityContext> jwkSource = new RemoteJWKSet<>(URI.create(jwksUri).toURL());
        JWSVerificationKeySelector<SecurityContext> keySelector =
                new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource);
        this.processor = new DefaultJWTProcessor<>();
        this.processor.setJWSKeySelector(keySelector);
        this.processor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
                new JWTClaimsSet.Builder().issuer(EXPECTED_ISSUER).audience(audience).build(),
                Set.of("sub", "iss", "aud", "exp")));
    }

    /**
     * Verifies the token's signature against the fetched JWKS and its
     * issuer/audience/expiry claims, throwing if the token is invalid.
     *
     * @return the verified claims, including the subject to authenticate as.
     */
    public JWTClaimsSet verify(String token) throws Exception {
        SignedJWT jwt = SignedJWT.parse(token);
        return processor.process(jwt, null);
    }
}
