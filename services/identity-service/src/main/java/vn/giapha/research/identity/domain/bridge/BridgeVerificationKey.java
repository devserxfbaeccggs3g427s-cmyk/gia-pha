package vn.giapha.research.identity.domain.bridge;

import java.security.PublicKey;

/**
 * One accepted bridge public key (Task 18.4). Multiple keys are valid
 * concurrently so the Next.js signer can rotate its private key without
 * downtime — a token signed by any accepted {@code kid} is verifiable as
 * long as its {@code exp} falls inside the bridge max-lifetime.
 */
public record BridgeVerificationKey(String keyId, PublicKey publicKey, long notAfterEpochSeconds) {

    public boolean accepts(long tokenEpochSeconds) {
        return notAfterEpochSeconds <= 0 || tokenEpochSeconds <= notAfterEpochSeconds;
    }
}
