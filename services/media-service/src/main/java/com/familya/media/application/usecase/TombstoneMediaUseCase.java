package com.familya.media.application.usecase;

import com.familya.media.application.port.in.TombstoneMediaCommand;
import com.familya.media.application.port.out.MediaAuthorization;
import com.familya.media.application.port.out.MediaChangePublisher;
import com.familya.media.application.port.out.MediaRepository;
import com.familya.media.application.port.out.MediaRetentionRepository;
import com.familya.media.domain.event.MediaDetached;
import com.familya.media.domain.exception.MediaNotFoundException;
import com.familya.media.domain.model.MediaAsset;
import com.familya.platform.error.ForbiddenException;
import com.familya.platform.error.OptimisticConcurrencyException;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Use case tombstone (xóa mềm) một media và đặt retention hold để
 * binary chỉ bị cleanup worker xóa sau khi hết hạn.
 *
 * <p>Đây là bước trước ranh giới không-thể-đảo (irreversible boundary)
 * trong quy trình xóa media: sau bước này media bị ẩn với client nhưng
 * binary vẫn còn trong grace period (mặc định 30 ngày).</p>
 *
 * <p>Luồng nghiệp vụ chính:</p>
 * <ol>
 *   <li>Ghi nhận metric mutation.</li>
 *   <li>Tra cứu {@code MediaAsset}; nếu không tồn tại → ném
 *       {@code MediaNotFoundException}.</li>
 *   <li>Phân quyền trên tree (kèm revision kỳ vọng).</li>
 *   <li>Đối chiếu {@code expectedVersion}; lệch → ném
 *       {@code OptimisticConcurrencyException}.</li>
 *   <li>Quyết định deadline hold: nếu caller không truyền
 *       {@code retentionHoldUntil} thì mặc định {@code now + 30 ngày}.</li>
 *   <li>Tombstone media và đặt retention hold với lý do
 *       {@code user-requested}.</li>
 *   <li>Phát sự kiện {@code MediaDetached} để downstream vô hiệu hóa
 *       tham chiếu và cập nhật search index.</li>
 * </ol>
 */
@Service
public class TombstoneMediaUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(TombstoneMediaUseCase.class);

    /** Deadline retention hold mặc định nếu caller không truyền. */
    private static final int DEFAULT_RETENTION_DAYS = 30;

    private final MediaRepository repo;
    private final MediaAuthorization authz;
    private final MediaChangePublisher publisher;
    private final MediaRetentionRepository retention;
    private final PlatformMetrics metrics;

    public TombstoneMediaUseCase(MediaRepository repo,
                                  MediaAuthorization authz,
                                  MediaChangePublisher publisher,
                                  MediaRetentionRepository retention,
                                  PlatformMetrics metrics) {
        this.repo = repo;
        this.authz = authz;
        this.publisher = publisher;
        this.retention = retention;
        this.metrics = metrics;
    }

    /**
     * Thực thi use case.
     *
     * @param cmd lệnh tombstone; xem {@link TombstoneMediaCommand}.
     * @throws MediaNotFoundException         nếu không tìm thấy media.
     * @throws ForbiddenException             nếu user không có quyền.
     * @throws OptimisticConcurrencyException nếu version không khớp.
     */
    @Transactional
    public void execute(TombstoneMediaCommand cmd) {
        // Bước 1: ghi nhận metric mutation đầu vào.
        metrics.mutationAccepted("media-service", "tombstone");
        // Bước 2: tra cứu media.
        MediaAsset asset = repo.findById(cmd.mediaId())
                .orElseThrow(() -> new MediaNotFoundException("Media " + cmd.mediaId() + " not found"));
        // Bước 3: phân quyền.
        MediaAuthorization.Decision d = authz.authorize(asset.treeId(), cmd.actingUser(), cmd.expectedTreeRevision());
        if (!d.isAllowed()) {
            throw new ForbiddenException("Cannot tombstone media: " + d.reason());
        }
        // Bước 4: optimistic concurrency.
        if (asset.version() != cmd.expectedVersion()) {
            throw new OptimisticConcurrencyException(
                    "Media " + cmd.mediaId() + " expected version " + cmd.expectedVersion() + " but found " + asset.version());
        }
        Instant now = Instant.now();
        // Bước 5: nếu caller không truyền retentionHoldUntil thì dùng
        // mặc định 30 ngày — cùng grace với purge tree để có hành vi
        // đồng nhất.
        Instant holdUntil = cmd.retentionHoldUntil() == null
                ? now.plus(DEFAULT_RETENTION_DAYS, ChronoUnit.DAYS)
                : cmd.retentionHoldUntil();
        // Bước 6: tombstone và đặt hold đồng thời trong transaction.
        repo.tombstone(cmd.mediaId(), now, asset.version());
        retention.placeHold(cmd.mediaId(), asset.treeId(), holdUntil, "user-requested");
        // Bước 7: phát sự kiện MediaDetached với lý do TOMBSTONE.
        publisher.publish(new MediaDetached(asset.treeId(), cmd.mediaId(), asset.version() + 1,
                now, "TOMBSTONE"));
        LOG.info("Tombstoned media mediaId={} tree={} holdUntil={}", cmd.mediaId(), asset.treeId(), holdUntil);
    }
}
