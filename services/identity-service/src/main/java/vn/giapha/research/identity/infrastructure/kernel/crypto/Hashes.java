package vn.giapha.research.identity.infrastructure.kernel.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class Hashes {
    private Hashes() {}

    public static byte[] sha256(byte[] input) {
        return digest().digest(input);
    }

    public static byte[] sha256(String input) {
        return sha256(input.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] sha256Canonical(String... parts) {
        MessageDigest digest = digest();
        for (String part : parts) {
            byte[] bytes = (part == null ? "" : part).getBytes(StandardCharsets.UTF_8);
            digest.update(new byte[] {
                    (byte) (bytes.length >>> 24), (byte) (bytes.length >>> 16),
                    (byte) (bytes.length >>> 8), (byte) bytes.length
            });
            digest.update(bytes);
        }
        return digest.digest();
    }

    public static String hex(byte[] hash) {
        return HexFormat.of().formatHex(hash);
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
