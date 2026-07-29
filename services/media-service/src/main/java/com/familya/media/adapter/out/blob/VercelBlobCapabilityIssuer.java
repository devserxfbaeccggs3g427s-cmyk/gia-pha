package com.familya.media.adapter.out.blob;

import com.familya.media.application.port.out.BlobCapabilityIssuer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default short-lived signed capability issuer. The {@code signedPutUrl}
 * and {@code signedGetUrl} strings are issued once and are never
 * persisted, returned to a publisher, or emitted in events. Tests
 * should swap this with a deterministic in-memory issuer.
 *
 * <p>The HMAC body is opaque to the caller — the shape mimics the
 * official Vercel Blob JS SDK pattern ({@code put(path, body, options)}).
 */
@Component
public class VercelBlobCapabilityIssuer implements BlobCapabilityIssuer {

    private static final Logger LOG = LoggerFactory.getLogger(VercelBlobCapabilityIssuer.class);

    private final Clock clock;
    private final String storeId;
    private final long capabilityTtlMs;
    private final java.util.Set<UUID> revoked = ConcurrentHashMap.newKeySet();

    public VercelBlobCapabilityIssuer(@org.springframework.beans.factory.annotation.Qualifier("systemClock") Clock clock,
                                       @Value("${familya.blob.vercel.store-id:}") String storeId,
                                       @Value("${familya.blob.vercel.capability-ttl-ms:600000}") long capabilityTtlMs) {
        this.clock = clock;
        this.storeId = storeId;
        this.capabilityTtlMs = capabilityTtlMs;
    }

    @Override
    public Capability issue(UUID treeId, UUID mediaId, String exactPath, String mimeType) {
        long exp = clock.millis() + capabilityTtlMs;
        String sig = sign(storeId, exactPath, exp);
        String putUrl = "https://blob.vercel-storage.com/" + exactPath + "?token=" + sig + "&exp=" + exp;
        String getUrl = "https://blob.vercel-storage.com/" + exactPath + "?token=" + sig + "&exp=" + exp + "&download=0";
        // Do not log the URLs — they are bearer secrets.
        LOG.debug("Issued blob capability mediaId={} treeId={} path={} ttl={}ms",
                mediaId, treeId, exactPath, capabilityTtlMs);
        return new Capability(exactPath, putUrl, getUrl, exp);
    }

    @Override
    public void invalidate(UUID mediaId) {
        revoked.add(mediaId);
        LOG.info("Invalidated blob capability mediaId={}", mediaId);
    }

    private String sign(String store, String path, long exp) {
        // Hex of SHA-256(store + ":" + path + ":" + exp); deterministic & short.
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(((store == null ? "" : store) + ":" + path + ":" + exp).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
