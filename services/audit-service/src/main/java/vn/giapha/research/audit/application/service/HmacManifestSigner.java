package vn.giapha.research.audit.application.service;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Default {@link ManifestSigner} backed by an HMAC-SHA-256 secret loaded
 * from {@code GIAPHA_MIGRATION_HMAC_KEY}. Production deploys should swap
 * this bean for a KMS-backed implementation so the signing key is not
 * embedded in the application secret store.
 */
public class HmacManifestSigner implements ManifestSigner {

    private static final String ALGO = "HmacSHA256";

    private final byte[] secret;

    public HmacManifestSigner(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("Manifest signing secret is required");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String sign(byte[] manifest) {
        try {
            Mac mac = Mac.getInstance(ALGO);
            mac.init(new SecretKeySpec(secret, ALGO));
            return HexFormat.of().formatHex(mac.doFinal(manifest));
        } catch (Exception invalid) {
            throw new IllegalStateException("Manifest signing failed", invalid);
        }
    }

    @Override
    public boolean verify(byte[] manifest, String signature) {
        return constantTimeEquals(sign(manifest), signature);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
