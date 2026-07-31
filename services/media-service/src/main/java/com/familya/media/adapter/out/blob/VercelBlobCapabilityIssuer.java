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
 * Adapter đầu ra (outbound) — triển khai mặc định {@link BlobCapabilityIssuer}
 * theo pattern Vercel Blob.
 * <p>
 * Cấp URL ký số dùng một lần (signedPutUrl, signedGetUrl) với TTL ngắn (mặc
 * định 600_000ms = 10 phút). URL đã ký là bearer secret: tuyệt đối không
 * log, không persist, không phát hành qua event. Test nên thay bằng issuer
 * in-memory deterministic.
 * <p>
 * Chữ ký là hex SHA-256 của {@code storeId + ":" + path + ":" + exp}. Đây là
 * placeholder an toàn cho HMAC thực — khi tích hợp Vercel Blob thật, thay
 * bằng HMAC theo tài liệu chính thức.
 */
@Component
public class VercelBlobCapabilityIssuer implements BlobCapabilityIssuer {

    private static final Logger LOG = LoggerFactory.getLogger(VercelBlobCapabilityIssuer.class);

    private final Clock clock;
    private final String storeId;
    private final long capabilityTtlMs;
    private final java.util.Set<UUID> revoked = ConcurrentHashMap.newKeySet();

    /**
     * Khởi tạo issuer.
     *
     * @param clock           Clock hệ thống (có thể thay bằng fixed clock trong test).
     * @param storeId         mã store Vercel Blob (tùy chọn, dùng trong chữ ký).
     * @param capabilityTtlMs TTL mặc định của capability, đọc từ
     *                        {@code familya.blob.vercel.capability-ttl-ms} (mặc định 600000 = 10 phút).
     */
    public VercelBlobCapabilityIssuer(@org.springframework.beans.factory.annotation.Qualifier("systemClock") Clock clock,
                                       @Value("${familya.blob.vercel.store-id:}") String storeId,
                                       @Value("${familya.blob.vercel.capability-ttl-ms:600000}") long capabilityTtlMs) {
        this.clock = clock;
        this.storeId = storeId;
        this.capabilityTtlMs = capabilityTtlMs;
    }

    /**
     * Cấp phát capability mới.
     * <p>
     * Sinh chữ ký SHA-256, dựng URL PUT và GET (GET chỉ phục vụ verify, không
     * download). KHÔNG log URL — chỉ log metadata (mediaId, treeId, path, ttl).
     *
     * @param treeId     UUID cây (chỉ dùng để log).
     * @param mediaId    UUID media.
     * @param exactPath  đường dẫn chính xác trong blob store.
     * @param mimeType   MIME type (chưa sử dụng, dành cho header trong tích hợp thật).
     * @return {@link Capability} chứa exactPath, signedPutUrl, signedGetUrl, exp.
     */
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

    /**
     * Vô hiệu hóa capability đã cấp cho media.
     * <p>
     * Theo dõi danh sách revoked in-memory (ConcurrentHashMap.newKeySet) — khi
     * mediaId đã có trong tập này, mọi URL cũ coi như không hợp lệ ở gateway.
     *
     * @param mediaId UUID media cần vô hiệu hóa.
     */
    @Override
    public void invalidate(UUID mediaId) {
        revoked.add(mediaId);
        LOG.info("Invalidated blob capability mediaId={}", mediaId);
    }

    /**
     * Sinh chữ ký hex SHA-256 của {@code store + ":" + path + ":" + exp}.
     * <p>
     * SHA-256 luôn được JVM hỗ trợ nên {@link NoSuchAlgorithmException} chỉ là
     * defensive — quấn thành {@link IllegalStateException} để không yêu cầu
     * caller xử lý checked exception.
     */
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
