package com.familya.media.application.usecase;

import com.familya.media.application.port.in.CreateUploadIntentCommand;
import com.familya.media.application.port.out.BlobCapabilityIssuer;
import com.familya.media.application.port.out.MediaAuthorization;
import com.familya.media.application.port.out.MediaChangePublisher;
import com.familya.media.application.port.out.MediaRepository;
import com.familya.media.domain.event.MediaQuarantined;
import com.familya.media.domain.exception.UploadTooLargeException;
import com.familya.media.domain.model.MediaAsset;
import com.familya.media.domain.model.MediaAsset.Kind;
import com.familya.media.domain.model.MediaAsset.Status;
import com.familya.platform.error.ForbiddenException;
import com.familya.platform.outbox.OutboxWriter;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Reserves a media row in {@code QUARANTINED} status and mints an
 * exact-path signed {@code put} capability. The capability is
 * returned to the caller exactly once and is never persisted.
 */
/**
 * Use case mở một "phiên upload" cho media mới: dựng bản ghi
 * {@code MediaAsset} ở trạng thái {@code QUARANTINED} và yêu cầu
 * {@link BlobCapabilityIssuer} cấp URL upload có chữ ký.
 *
 * <p>Luồng nghiệp vụ chính:</p>
 * <ol>
 *   <li>Ghi nhận metric mutation.</li>
 *   <li>Kiểm tra {@code byteSize} không vượt giới hạn cấu hình
 *       ({@code maxByteSize}, mặc định {@code 10485760} bytes = 10 MiB).</li>
 *   <li>Tra cứu quyền của {@code actingUser} trên tree (kèm revision).</li>
 *   <li>Sinh {@code mediaId} mới, dựng {@code exactPath} deterministic
 *       {@code quarantine/{treeId}/{mediaId}}, phân loại {@code Kind}
 *       theo MIME.</li>
 *   <li>Chèn {@code MediaAsset} với {@code status=QUARANTINED},
 *       {@code version=0}.</li>
 *   <li>Cấp capability (signed PUT URL + deadline). URL là bearer
 *       secret: trả về đúng một lần, không log.</li>
 *   <li>Phát sự kiện {@code MediaQuarantined} với lý do
 *       {@code INTENT_OPENED}.</li>
 * </ol>
 */
@Service
public class CreateUploadIntentUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(CreateUploadIntentUseCase.class);

    private final MediaRepository repo;
    private final BlobCapabilityIssuer issuer;
    private final MediaAuthorization authz;
    private final MediaChangePublisher publisher;
    private final OutboxWriter outbox;
    private final PlatformMetrics metrics;
    private final long maxByteSize;

    public CreateUploadIntentUseCase(MediaRepository repo,
                                     BlobCapabilityIssuer issuer,
                                     MediaAuthorization authz,
                                     MediaChangePublisher publisher,
                                     OutboxWriter outbox,
                                     PlatformMetrics metrics,
                                     @Value("${familya.media.upload.max-byte-size:10485760}") long maxByteSize) {
        this.repo = repo;
        this.issuer = issuer;
        this.authz = authz;
        this.publisher = publisher;
        this.outbox = outbox;
        this.metrics = metrics;
        this.maxByteSize = maxByteSize;
    }

    /**
     * Thực thi use case.
     *
     * @param cmd lệnh mở phiên upload; xem {@link CreateUploadIntentCommand}.
     * @return {@link Result} chứa mediaId, exactPath, signedPutUrl (bearer
     *         secret) và deadline hiệu lực.
     * @throws UploadTooLargeException nếu byteSize vượt {@code maxByteSize}.
     * @throws ForbiddenException      nếu user không có quyền.
     */
    @Transactional
    public Result execute(CreateUploadIntentCommand cmd) {
        metrics.mutationAccepted("media-service", "createUploadIntent");
        // Bước 1: kiểm tra kích thước tối đa. Mặc định 10485760 bytes
        // (10 MiB) có thể cấu hình qua property
        // familya.media.upload.max-byte-size.
        if (cmd.byteSize() > maxByteSize) {
            throw new UploadTooLargeException(
                    "Upload exceeds limit of " + maxByteSize + " bytes (got " + cmd.byteSize() + ")");
        }
        // Bước 2: phân quyền trên tree.
        MediaAuthorization.Decision d = authz.authorize(cmd.treeId(), cmd.actingUser(), cmd.expectedTreeRevision());
        if (!d.isAllowed()) {
            throw new ForbiddenException("Cannot create upload intent: " + d.reason());
        }
        // Bước 3: dựng đường dẫn deterministic để capability có thể ký
        // sẵn path trước khi blob tồn tại (exact-path pattern).
        UUID mediaId = UUID.randomUUID();
        Instant now = Instant.now();
        String path = buildPath(cmd.treeId(), mediaId);
        // Bước 4: chèn MediaAsset ở trạng thái QUARANTINED, version=0,
        // promoted=false; retentionHoldUntil=null (chưa tombstone).
        MediaAsset asset = new MediaAsset(
                mediaId, cmd.treeId(), null, cmd.actingUser(),
                classifyKind(cmd.mimeType()), cmd.mimeType(),
                cmd.byteSize(), cmd.sha256(), cmd.originalFilename(),
                Status.QUARANTINED, path, false, null, null, now, now, 0L);
        repo.insert(asset);
        // Bước 5: cấp capability. signedPutUrl là bearer secret; trả về
        // đúng một lần cho caller, không log nội dung.
        BlobCapabilityIssuer.Capability capability = issuer.issue(cmd.treeId(), mediaId, path, cmd.mimeType());
        // Bước 6: phát sự kiện MediaQuarantined để downstream theo dõi
        // trạng thái upload.
        publisher.publish(new MediaQuarantined(cmd.treeId(), mediaId, 1L, now, "INTENT_OPENED", "awaiting upload"));
        LOG.info("Created upload intent mediaId={} tree={} actingUser={} path={}",
                mediaId, cmd.treeId(), cmd.actingUser(), path);
        return new Result(mediaId, path, capability.signedPutUrl(), capability.expiresAtEpochMs());
    }

    /**
     * Phân loại {@code Kind} của media dựa trên MIME type.
     *
     * <p>Quy tắc: {@code image/*} → {@code PHOTO}, {@code video/*} →
     * {@code VIDEO}, {@code audio/*} → {@code AUDIO},
     * {@code application/pdf} hoặc {@code text/*} → {@code DOCUMENT};
     * còn lại (kể cả MIME null) → {@code OTHER}.</p>
     *
     * @param mime MIME type khai báo.
     * @return {@code Kind} tương ứng.
     */
    private static Kind classifyKind(String mime) {
        if (mime == null) return Kind.OTHER;
        if (mime.startsWith("image/")) return Kind.PHOTO;
        if (mime.startsWith("video/")) return Kind.VIDEO;
        if (mime.startsWith("audio/")) return Kind.AUDIO;
        if (mime.startsWith("application/pdf") || mime.startsWith("text/")) return Kind.DOCUMENT;
        return Kind.OTHER;
    }

    /**
     * Dựng đường dẫn exact-path trong blob store cho media đang quarantine.
     *
     * @param treeId  UUID family-tree.
     * @param mediaId UUID media.
     * @return path có dạng {@code quarantine/{treeId}/{mediaId}}.
     */
    private static String buildPath(UUID treeId, UUID mediaId) {
        return "quarantine/" + treeId + "/" + mediaId;
    }

    /**
     * Kết quả trả về của use case.
     *
     * @param mediaId         UUID media vừa tạo.
     * @param exactPath       đường dẫn exact-path trong blob store.
     * @param signedPutUrl    URL upload có chữ ký; bearer secret, KHÔNG log.
     * @param expiresAtEpochMs deadline hiệu lực (epoch millis).
     */
    public record Result(UUID mediaId, String exactPath, String signedPutUrl, long expiresAtEpochMs) { }
}
