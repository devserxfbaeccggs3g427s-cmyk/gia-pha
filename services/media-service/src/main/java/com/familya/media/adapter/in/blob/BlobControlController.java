package com.familya.media.adapter.in.blob;

import com.familya.media.application.port.out.BlobCapabilityIssuer;
import com.familya.media.application.port.out.MediaAuthorization;
import com.familya.media.application.port.out.MediaRepository;
import com.familya.platform.error.ForbiddenException;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Adapter REST đầu vào (inbound) — Cổng điều khiển Blob (Blob Control Gateway).
 * <p>
 * Cấp phát các "capability" (quyền) ký số dùng một lần để trình duyệt tải tệp
 * trực tiếp lên vùng cách ly (quarantine) của Vercel Blob mà không cần đi qua
 * media-service. URL đã ký được trả về trong response body và tuyệt đối
 * không được log lại dưới bất kỳ hình thức nào.
 * <p>
 * Giao thức: HTTP/JSON, base path {@code /api/v2/media/blob}.
 *
 * @author gia-pha platform team
 */
@RestController
@RequestMapping(path = "/api/v2/media/blob", produces = MediaType.APPLICATION_JSON_VALUE)
public class BlobControlController {

    private static final Logger LOG = LoggerFactory.getLogger(BlobControlController.class);

    private final BlobCapabilityIssuer issuer;
    private final MediaAuthorization authz;
    private final MediaRepository repo;
    private final PlatformMetrics metrics;
    private final long maxByteSize;

    /**
     * Khởi tạo controller với các phụ thuộc cần thiết để cấp phát capability.
     *
     * @param issuer     cổng cấp capability ký số (Vercel Blob hoặc mock test).
     * @param authz      cổng phân quyền dựa trên projection (xem {@link ProjectionMediaAuthorization}).
     * @param repo       repository truy vấn {@code MediaAsset}.
     * @param metrics    bộ thu thập chỉ số nền tảng để ghi nhận capability issued/denied.
     * @param maxByteSize kích thước tối đa (bytes) cho phép cấp capability; mặc định 10 MiB
     *                    (10485760) có thể cấu hình qua {@code familya.media.upload.max-byte-size}.
     */
    public BlobControlController(BlobCapabilityIssuer issuer, MediaAuthorization authz,
                                  MediaRepository repo, PlatformMetrics metrics,
                                  @Value("${familya.media.upload.max-byte-size:10485760}") long maxByteSize) {
        this.issuer = issuer;
        this.authz = authz;
        this.repo = repo;
        this.metrics = metrics;
        this.maxByteSize = maxByteSize;
    }

    /**
     * Endpoint cấp phát capability PUT một lần cho mediaId chỉ định.
     * <p>
     * Luồng xử lý:
     * <ol>
     *   <li>Tra cứu {@code MediaAsset}; trả 403 (qua {@link ForbiddenException}) nếu không tồn tại
     *       — vì lý do bảo mật không phân biệt "không tồn tại" và "không có quyền".</li>
     *   <li>Phân quyền theo (treeId, actingUser, expectedTreeRevision).</li>
     *   <li>Kiểm tra kích thước asset có vượt ngưỡng {@code maxByteSize} hay không.</li>
     *   <li>Phát hành capability với TTL mặc định 600_000ms (10 phút) hoặc theo body yêu cầu.</li>
     *   <li>Ghi log audit KHÔNG bao gồm URL — chỉ ghi mediaId/tree/ttl.</li>
     * </ol>
     *
     * @param actingUser          UUID người dùng thực hiện (header {@code X-Acting-User}).
     * @param mediaId             UUID media cần cấp capability (path variable).
     * @param expectedTreeRevision revision kỳ vọng của cây gia phả (header {@code X-Tree-Revision},
     *                            tùy chọn — mặc định 0 nghĩa là không kiểm tra staleness).
     * @param req                 payload tùy chọn chứa {@code ttlMs}; có thể null.
     * @return {@link CapabilityResponse} chứa mediaId, exactPath, signedPutUrl, expiresAtEpochMs.
     * @throws com.familya.platform.error.ForbiddenException nếu media không tồn tại
     *         hoặc phân quyền không cho phép (403).
     * @throws IllegalArgumentException nếu asset vượt giới hạn kích thước (400/422).
     */
    @PostMapping(path = "/{mediaId}/capability", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CapabilityResponse> issueCapability(@RequestHeader("X-Acting-User") UUID actingUser,
                                                              @PathVariable UUID mediaId,
                                                              @RequestHeader(value = "X-Tree-Revision", required = false) Long expectedTreeRevision,
                                                              @RequestBody(required = false) CapabilityRequest req) {
        // Tra cứu asset; dùng ForbiddenException thay vì NotFound để tránh lộ thông tin tồn tại.
        var asset = repo.findById(mediaId)
                .orElseThrow(() -> new ForbiddenException("Media " + mediaId + " not found"));
        // Phân quyền: kết hợp role, revoked, projection revision vs expected.
        MediaAuthorization.Decision d = authz.authorize(asset.treeId(), actingUser,
                expectedTreeRevision == null ? 0L : expectedTreeRevision);
        if (!d.isAllowed()) {
            metrics.capabilityIssued("media-service", "denied");
            throw new ForbiddenException("Cannot issue blob capability: " + d.reason());
        }
        // Chặn capability cho asset quá lớn để giảm thiểu rủi ro lạm dụng/DoS.
        if (asset.byteSize() > maxByteSize) {
            metrics.capabilityIssued("media-service", "denied-size");
            throw new IllegalArgumentException("Media exceeds size limit");
        }
        // TTL mặc định 10 phút; client có thể yêu cầu ngắn hơn nhưng không vượt quá cấu hình issuer.
        long ttl = req == null || req.ttlMs() == null ? 600_000L : req.ttlMs();
        BlobCapabilityIssuer.Capability capability = issuer.issue(asset.treeId(), mediaId, asset.quarantinePath(), asset.mimeType());
        metrics.capabilityIssued("media-service", "issued");
        // CHÚ Ý: không log signedPutUrl — đây là bearer secret.
        LOG.info("Issued blob capability mediaId={} tree={} ttl={}ms", mediaId, asset.treeId(), ttl);
        return ResponseEntity.ok(new CapabilityResponse(
                mediaId, asset.quarantinePath(), capability.signedPutUrl(), capability.expiresAtEpochMs()));
    }

    /**
     * Endpoint vô hiệu hóa (invalidate) capability PUT đã cấp cho mediaId.
     * <p>
     * Được dùng khi người dùng hủy upload, khi media bị tombstone, hoặc khi phát
     * hiện dấu hiệu lạm dụng. Idempotent — gọi nhiều lần vẫn an toàn.
     *
     * @param mediaId UUID media cần vô hiệu hóa capability.
     * @return {@code 204 No Content} khi thành công.
     */
    @DeleteMapping("/{mediaId}/capability")
    public ResponseEntity<Void> invalidate(@PathVariable UUID mediaId) {
        issuer.invalidate(mediaId);
        return ResponseEntity.noContent().build();
    }

    /** Payload yêu cầu cấp capability (tùy chọn). {@code ttlMs} định thời gian sống của URL ký. */
    public record CapabilityRequest(Long ttlMs) { }

    /** Phản hồi capability: mediaId, đường dẫn chính xác, URL ký và thời điểm hết hạn (epoch ms). */
    public record CapabilityResponse(UUID mediaId, String exactPath, String signedPutUrl, long expiresAtEpochMs) { }
}
