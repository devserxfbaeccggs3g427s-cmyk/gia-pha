package vn.giapha.research.identity.application.bridge;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * Compact JOSE ES256 (P-256 + SHA-256 ECDSA) encoder/decoder. JDK-only —
 * no Nimbus/Auth0 dependency (ADR-003). Validates the algorithm tag, header
 * shape and signature on every call and is the single place that knows the
 * ES256 wire format. The signature is converted between the JOSE R || S
 * encoding and the JCA {@code DER} encoding so the JDK verifier and
 * verifier implementations match byte-for-byte.
 *
 * <p>This is deliberately a thin codec: callers handle claim validation,
 * issuer/audience checks and replay protection (Task 18).
 */
public final class BridgeEs256 {

    public static final String ALG = "ES256";
    public static final String CURVE = "secp256r1";
    public static final String JCA_SIGNATURE = "SHA256withECDSA";

    private BridgeEs256() {
    }

    /** Produce a fresh ES256 key pair (development and rotation drill only). */
    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec(CURVE));
            return generator.generateKeyPair();
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException("EC key generation unavailable", unavailable);
        }
    }

    /** Encode an ES256 JWS compact serialization: {@code base64url(header).base64url(payload).base64url(sig)}. */
    public static String sign(PrivateKey privateKey, String keyId, String payloadJson) {
        if (!"EC".equalsIgnoreCase(privateKey.getAlgorithm())) {
            throw new IllegalArgumentException("ES256 requires an EC private key");
        }
        String headerJson = "{\"alg\":\"" + ALG + "\",\"typ\":\"JWT\",\"kid\":\"" + escape(keyId) + "\"}";
        String headerSegment = base64Url(headerJson.getBytes(StandardCharsets.UTF_8));
        String payloadSegment = base64Url(payloadJson.getBytes(StandardCharsets.UTF_8));
        String signingInput = headerSegment + "." + payloadSegment;
        try {
            Signature signature = Signature.getInstance(JCA_SIGNATURE);
            signature.initSign(privateKey);
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            byte[] der = signature.sign();
            byte[] rs = derToJose(der, 64);
            String signatureSegment = base64Url(rs);
            return signingInput + "." + signatureSegment;
        } catch (GeneralSecurityException signing) {
            throw new IllegalStateException("ES256 signing failed", signing);
        }
    }

    /**
     * Verify a compact JWS and return the decoded payload bytes. Throws on any
     * header/alg mismatch, malformed segment, unsupported key shape or bad
     * signature. The verifier does not validate {@code exp} / {@code iss} /
     * {@code aud} — the caller (Task 18) is responsible for policy.
     */
    public static Decoded verify(PublicKey publicKey, String keyId, String compact) {
        String[] parts = compact.split("\\.");
        if (parts.length != 3) {
            throw new BridgeVerificationException("JWT must have three segments");
        }
        byte[] headerBytes = base64UrlDecode(parts[0]);
        JsonMap header = JsonMap.parse(new String(headerBytes, StandardCharsets.UTF_8));
        String alg = header.requireString("alg");
        String headerKid = header.string("kid");
        if (!ALG.equals(alg)) {
            throw new BridgeVerificationException("Unsupported alg: " + alg);
        }
        if (headerKid != null && keyId != null && !headerKid.equals(keyId)) {
            throw new BridgeVerificationException("kid mismatch: " + headerKid);
        }
        byte[] payload = base64UrlDecode(parts[1]);
        byte[] signatureBytes = base64UrlDecode(parts[2]);
        byte[] der;
        try {
            der = joseToDer(signatureBytes);
        } catch (IllegalArgumentException malformed) {
            throw new BridgeVerificationException("Malformed ES256 signature", malformed);
        }
        String signingInput = parts[0] + "." + parts[1];
        try {
            Signature verifier = Signature.getInstance(JCA_SIGNATURE);
            verifier.initVerify(publicKey);
            verifier.update(signingInput.getBytes(StandardCharsets.UTF_8));
            if (!verifier.verify(der)) {
                throw new BridgeVerificationException("Bad signature");
            }
        } catch (GeneralSecurityException verifyFailed) {
            throw new BridgeVerificationException("Signature verification failed", verifyFailed);
        }
        return new Decoded(payload, headerKid);
    }

    public record Decoded(byte[] payload, String keyId) {}

    /** Load an EC public key from JWK-style {@code (x, y)} coordinates (base64url). */
    public static PublicKey publicKeyFromCoordinates(String x, String y) {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec(CURVE));
            ECParameterSpec spec = parameters.getParameterSpec(ECParameterSpec.class);
            BigInteger ax = new BigInteger(1, base64UrlDecode(x));
            BigInteger ay = new BigInteger(1, base64UrlDecode(y));
            ECPublicKeySpec keySpec = new ECPublicKeySpec(new ECPoint(ax, ay), spec);
            return KeyFactory.getInstance("EC").generatePublic(keySpec);
        } catch (GeneralSecurityException invalid) {
            throw new IllegalArgumentException("Invalid ES256 public key", invalid);
        }
    }

    /** Convert a {@code (d, x, y)} private key triple into a JDK key pair. */
    public static KeyPair privateKeyFromCoordinates(String d, String x, String y) {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec(CURVE));
            ECParameterSpec spec = parameters.getParameterSpec(ECParameterSpec.class);
            BigInteger ax = new BigInteger(1, base64UrlDecode(x));
            BigInteger ay = new BigInteger(1, base64UrlDecode(y));
            ECPublicKeySpec pubSpec = new ECPublicKeySpec(new ECPoint(ax, ay), spec);
            BigInteger ad = new BigInteger(1, base64UrlDecode(d));
            ECPrivateKeySpec privSpec = new ECPrivateKeySpec(ad, spec);
            KeyFactory factory = KeyFactory.getInstance("EC");
            return new KeyPair(factory.generatePublic(pubSpec), factory.generatePrivate(privSpec));
        } catch (GeneralSecurityException invalid) {
            throw new IllegalArgumentException("Invalid ES256 private key", invalid);
        }
    }

    /** Read the {@code (x, y)} coordinates out of an {@link ECPublicKey}. */
    public static String[] exportCoordinates(PublicKey publicKey) {
        if (!(publicKey instanceof ECPublicKey ec)) {
            throw new IllegalArgumentException("Expected ECPublicKey");
        }
        ECPoint point = ec.getW();
        return new String[] {
                base64Url(encodeUnsigned(point.getAffineX())),
                base64Url(encodeUnsigned(point.getAffineY()))
        };
    }

    /** Read the {@code (d)} coordinate out of an {@link ECPrivateKey}. */
    public static String exportPrivateCoordinate(PrivateKey privateKey) {
        if (!(privateKey instanceof ECPrivateKey ec)) {
            throw new IllegalArgumentException("Expected ECPrivateKey");
        }
        return base64Url(encodeUnsigned(ec.getS()));
    }

    /* --- JOSE <-> DER conversions -------------------------------------- */

    static byte[] derToJose(byte[] der, int fixedSize) {
        // Expect DER SEQUENCE { INTEGER r, INTEGER s } — extract r,s.
        if (der.length < 8 || der[0] != 0x30) {
            throw new IllegalArgumentException("Missing DER SEQUENCE");
        }
        int idx = 2;
        if (der[idx++] != 0x02) {
            throw new IllegalArgumentException("Missing INTEGER r");
        }
        int rLen = der[idx++] & 0xff;
        byte[] r = stripLeadingZeros(der, idx, rLen);
        idx += rLen;
        if (der[idx++] != 0x02) {
            throw new IllegalArgumentException("Missing INTEGER s");
        }
        int sLen = der[idx++] & 0xff;
        byte[] s = stripLeadingZeros(der, idx, sLen);
        byte[] rPadded = leftPad(r, fixedSize);
        byte[] sPadded = leftPad(s, fixedSize);
        byte[] out = new byte[fixedSize * 2];
        System.arraycopy(rPadded, 0, out, 0, fixedSize);
        System.arraycopy(sPadded, 0, out, fixedSize, fixedSize);
        return out;
    }

    static byte[] joseToDer(byte[] jose) {
        if (jose.length == 0 || (jose.length % 2) != 0) {
            throw new IllegalArgumentException("ES256 JOSE signature must be r||s");
        }
        int half = jose.length / 2;
        byte[] r = stripLeadingZeros(jose, 0, half);
        byte[] s = stripLeadingZeros(jose, half, half);
        return derEncode(r, s);
    }

    private static byte[] derEncode(byte[] r, byte[] s) {
        int size = 2 + lengthBytes(r.length) + r.length + 2 + lengthBytes(s.length) + s.length;
        byte[] out = new byte[size + 2];
        int idx = 0;
        out[idx++] = 0x30;
        out[idx++] = (byte) (size & 0xff);
        out[idx++] = 0x02;
        out[idx++] = (byte) r.length;
        System.arraycopy(r, 0, out, idx, r.length);
        idx += r.length;
        out[idx++] = 0x02;
        out[idx++] = (byte) s.length;
        System.arraycopy(s, 0, out, idx, s.length);
        return out;
    }

    private static int lengthBytes(int length) {
        return 1;
    }

    private static byte[] stripLeadingZeros(byte[] src, int offset, int length) {
        int start = offset;
        int end = offset + length;
        while (start < end && src[start] == 0) {
            start++;
        }
        int newLen = end - start;
        // Add a leading 0x00 if the high bit is set so the integer is positive.
        if (newLen > 0 && (src[start] & 0x80) != 0) {
            byte[] out = new byte[newLen + 1];
            out[0] = 0;
            System.arraycopy(src, start, out, 1, newLen);
            return out;
        }
        return Arrays.copyOfRange(src, start, end);
    }

    private static byte[] leftPad(byte[] src, int size) {
        if (src.length == size) {
            return src;
        }
        if (src.length > size) {
            return Arrays.copyOfRange(src, src.length - size, src.length);
        }
        byte[] out = new byte[size];
        System.arraycopy(src, 0, out, size - src.length, src.length);
        return out;
    }

    private static byte[] encodeUnsigned(BigInteger value) {
        byte[] raw = value.toByteArray();
        if (raw.length > 0 && raw[0] == 0) {
            return Arrays.copyOfRange(raw, 1, raw.length);
        }
        return raw;
    }

    /* --- Base64URL helpers --------------------------------------------- */

    static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static byte[] base64UrlDecode(String segment) {
        try {
            return Base64.getUrlDecoder().decode(segment);
        } catch (IllegalArgumentException malformed) {
            throw new BridgeVerificationException("Malformed base64url segment", malformed);
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
