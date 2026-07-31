package com.familya.media.application.usecase;

import com.familya.media.application.port.in.VerifyUploadedCommand;
import com.familya.media.application.port.out.MediaAuthorization;
import com.familya.media.application.port.out.MediaChangePublisher;
import com.familya.media.application.port.out.MediaRepository;
import com.familya.media.domain.event.MediaQuarantined;
import com.familya.media.domain.exception.MediaNotFoundException;
import com.familya.media.domain.model.MediaAsset;
import com.familya.media.domain.model.MediaAsset.Status;
import com.familya.platform.error.ForbiddenException;
import com.familya.platform.error.OptimisticConcurrencyException;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Use case xác minh rằng blob đã upload xong khớp với intent ban đầu
 * (sha256 + byteSize) và chuyển media sang trạng thái
 * {@code SCANNING} để pipeline scan tiếp tục.
 *
 * <p>Luồng nghiệp vụ chính:</p>
 * <ol>
 *   <li>Ghi nhận metric mutation.</li>
 *   <li>Tra cứu {@code MediaAsset}; nếu không tồn tại → ném
 *       {@code MediaNotFoundException}.</li>
 *   <li>Phân quyền trên tree (kèm revision kỳ vọng).</li>
 *   <li>Đối chiếu {@code expectedVersion}; lệch → ném
 *       {@code OptimisticConcurrencyException}.</li>
 *   <li>So sánh sha256 thực tế với intent (không phân biệt hoa thường);
 *       lệch → mark FAILED, phát event {@code VERIFICATION_FAILED}, ném
 *       {@code IllegalStateException}.</li>
 *   <li>So sánh byteSize thực tế với intent; lệch → tương tự bước 5.</li>
 *   <li>Nếu khớp: chuyển {@code SCANNING}, phát event
 *       {@code VERIFIED_AWAITING_SCAN} để worker scan tiếp tục.</li>
 * </ol>
 */
@Service
public class VerifyUploadedUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(VerifyUploadedUseCase.class);

    private final MediaRepository repo;
    private final MediaAuthorization authz;
    private final MediaChangePublisher publisher;
    private final PlatformMetrics metrics;

    public VerifyUploadedUseCase(MediaRepository repo, MediaAuthorization authz,
                                  MediaChangePublisher publisher, PlatformMetrics metrics) {
        this.repo = repo;
        this.authz = authz;
        this.publisher = publisher;
        this.metrics = metrics;
    }

    /**
     * Thực thi use case.
     *
     * @param cmd lệnh verify; xem {@link VerifyUploadedCommand}.
     * @throws MediaNotFoundException         nếu không tìm thấy media.
     * @throws ForbiddenException             nếu user không có quyền.
     * @throws OptimisticConcurrencyException nếu version không khớp.
     * @throws IllegalStateException          nếu sha256 hoặc byteSize
     *                                        không khớp intent.
     */
    @Transactional
    public void execute(VerifyUploadedCommand cmd) {
        // Bước 1: ghi nhận metric mutation đầu vào.
        metrics.mutationAccepted("media-service", "verifyUploaded");
        // Bước 2: tra cứu media.
        MediaAsset asset = repo.findById(cmd.mediaId())
                .orElseThrow(() -> new MediaNotFoundException("Media " + cmd.mediaId() + " not found"));
        // Bước 3: phân quyền.
        MediaAuthorization.Decision d = authz.authorize(asset.treeId(), cmd.actingUser(), cmd.expectedTreeRevision());
        if (!d.isAllowed()) {
            throw new ForbiddenException("Cannot verify media: " + d.reason());
        }
        // Bước 4: optimistic concurrency.
        if (asset.version() != cmd.expectedVersion()) {
            throw new OptimisticConcurrencyException(
                    "Media " + cmd.mediaId() + " expected version " + cmd.expectedVersion() + " but found " + asset.version());
        }
        // Bước 5: so sánh sha256 không phân biệt hoa thường (một số client
        // gửi hex uppercase). Lệch → mark FAILED + phát event + ném.
        if (!asset.sha256().equalsIgnoreCase(cmd.sha256())) {
            repo.markFailed(cmd.mediaId(), asset.version(), Instant.now(), "sha256 mismatch");
            publisher.publish(new MediaQuarantined(asset.treeId(), cmd.mediaId(), asset.version() + 1,
                    Instant.now(), "VERIFICATION_FAILED", "sha256 mismatch"));
            throw new IllegalStateException("Uploaded sha256 does not match intent sha256 for media " + cmd.mediaId());
        }
        // Bước 6: so sánh byteSize. Lệch → tương tự bước 5.
        if (asset.byteSize() != cmd.byteSize()) {
            repo.markFailed(cmd.mediaId(), asset.version(), Instant.now(), "byte size mismatch");
            publisher.publish(new MediaQuarantined(asset.treeId(), cmd.mediaId(), asset.version() + 1,
                    Instant.now(), "VERIFICATION_FAILED", "byte size mismatch"));
            throw new IllegalStateException("Uploaded byte size does not match intent for media " + cmd.mediaId());
        }
        // Bước 7: khớp → chuyển SCANNING + phát event để worker scan.
        repo.markScanning(cmd.mediaId(), asset.version(), Instant.now());
        publisher.publish(new MediaQuarantined(asset.treeId(), cmd.mediaId(), asset.version() + 1,
                Instant.now(), "VERIFIED_AWAITING_SCAN", null));
        LOG.info("Verified upload mediaId={} tree={}", cmd.mediaId(), asset.treeId());
    }
}
