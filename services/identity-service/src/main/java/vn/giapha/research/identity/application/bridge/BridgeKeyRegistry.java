package vn.giapha.research.identity.application.bridge;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import vn.giapha.research.identity.domain.bridge.BridgeVerificationKey;

/**
 * Registry of bridge signing/verification keys (Task 18.4). Multiple keys are
 * valid concurrently so the Next.js signer can rotate its private key without
 * downtime. The first key in the configuration list is the active signing
 * key — all entries participate in verification.
 *
 * <p>In-memory only on purpose: the registry is rebuilt at startup from the
 * pinned secret store. Operator rotation is performed by publishing a new
 * configuration that lists both old and new public keys, then dropping the
 * old entry once all outstanding bridge tokens have expired
 * (max-lifetime ≈ 5 minutes).
 */
public class BridgeKeyRegistry {

    private final Map<String, PrivateKey> signingKeys = new ConcurrentHashMap<>();
    private final Map<String, BridgeVerificationKey> verificationKeys = new ConcurrentHashMap<>();
    private volatile String activeSigningKeyId;

    public synchronized void install(String activeKeyId, PrivateKey signingKey,
            Collection<BridgeVerificationKey> verificationKeys) {
        Objects.requireNonNull(activeKeyId, "activeKeyId");
        if (signingKey == null) {
            throw new IllegalArgumentException("signingKey is required");
        }
        if (verificationKeys == null || verificationKeys.isEmpty()) {
            throw new IllegalArgumentException("At least one verification key is required");
        }
        boolean activeKeyPresent = false;
        for (BridgeVerificationKey key : verificationKeys) {
            this.verificationKeys.put(key.keyId(), key);
            if (key.keyId().equals(activeKeyId)) {
                activeKeyPresent = true;
            }
        }
        if (!activeKeyPresent) {
            throw new IllegalArgumentException("Active signing key must be in the verification set");
        }
        this.signingKeys.put(activeKeyId, signingKey);
        this.activeSigningKeyId = activeKeyId;
    }

    public synchronized void installSelfSigned(List<String> orderedKeyIds) {
        if (orderedKeyIds == null || orderedKeyIds.isEmpty()) {
            throw new IllegalArgumentException("At least one key is required");
        }
        Map<String, KeyPair> generated = new LinkedHashMap<>();
        for (String keyId : orderedKeyIds) {
            generated.put(keyId, BridgeEs256.generateKeyPair());
        }
        String activeKeyId = orderedKeyIds.get(0);
        PrivateKey activePrivate = generated.get(activeKeyId).getPrivate();
        Collection<BridgeVerificationKey> verification = generated.entrySet().stream()
                .map(entry -> new BridgeVerificationKey(entry.getKey(),
                        entry.getValue().getPublic(), 0))
                .toList();
        install(activeKeyId, activePrivate, verification);
    }

    public synchronized void replaceVerificationKeys(Collection<BridgeVerificationKey> keys) {
        if (keys == null || keys.isEmpty()) {
            throw new IllegalArgumentException("At least one verification key is required");
        }
        for (BridgeVerificationKey key : keys) {
            verificationKeys.put(key.keyId(), key);
        }
    }

    public synchronized void rotateActiveKey(String newActiveKeyId, PrivateKey signingKey) {
        if (newActiveKeyId == null || signingKey == null) {
            throw new IllegalArgumentException("newActiveKeyId and signingKey are required");
        }
        if (!verificationKeys.containsKey(newActiveKeyId)) {
            throw new IllegalArgumentException("New key must be added to the verification set first");
        }
        signingKeys.put(newActiveKeyId, signingKey);
        activeSigningKeyId = newActiveKeyId;
    }

    public String activeSigningKeyId() {
        String id = activeSigningKeyId;
        if (id == null) {
            throw new IllegalStateException("No bridge signing key installed");
        }
        return id;
    }

    public boolean hasSigningKey(String keyId) {
        return signingKeys.containsKey(keyId);
    }

    public PrivateKey signingKey(String keyId) {
        PrivateKey key = signingKeys.get(keyId);
        if (key == null) {
            throw new IllegalArgumentException("Unknown signing key: " + keyId);
        }
        return key;
    }

    public PublicKey verificationKey(String keyId) {
        BridgeVerificationKey entry = verificationKeys.get(keyId);
        if (entry == null) {
            throw new BridgeVerificationException("Unknown verification key: " + keyId);
        }
        return entry.publicKey();
    }

    public Optional<BridgeVerificationKey> verificationKeyEntry(String keyId) {
        return Optional.ofNullable(verificationKeys.get(keyId));
    }

    public Collection<BridgeVerificationKey> verificationKeys() {
        return List.copyOf(verificationKeys.values());
    }
}
