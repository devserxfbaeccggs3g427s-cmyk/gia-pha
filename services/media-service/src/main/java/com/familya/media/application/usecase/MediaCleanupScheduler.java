package com.familya.media.application.usecase;

import com.familya.media.application.port.out.MediaBinaryReplicationRepository;
import com.familya.media.application.port.out.MediaReferenceRepository;
import com.familya.media.application.port.out.MediaRetentionRepository;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Job định kỳ chạy cleanup các retention hold đã đến hạn.
 *
 * <p>Luồng nghiệp vụ chính (mỗi tick):</p>
 * <ol>
 *   <li>Lấy tối đa {@code 200} retention hold đã đến deadline
 *       {@code holdUntil <= now}.</li>
 *   <li>Với mỗi hold: gọi {@code retention.release} để giải phóng. Lỗi
 *       được log nhưng không làm gián đoạn tick; lần sau sẽ retry.</li>
 *   <li>Tăng metric {@code cleanupReleased} cho mỗi lần release thành
 *       công.</li>
 * </ol>
 *
 * <p>Các phương thức {@link #enqueueReplication} và {@link #purgeReferences}
 * là helper cho use case khác gọi (ví dụ {@code ScanAndPromoteUseCase}
 * dùng {@code enqueueReplication}).</p>
 *
 * <p>Tần suất mặc định: {@code 60000} ms (1 phút), cấu hình qua
 * {@code familya.media.cleanup.interval-ms}.</p>
 */
@Component
public class MediaCleanupScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(MediaCleanupScheduler.class);

    /** Số hold xử lý tối đa mỗi tick; tránh lock DB quá lâu. */
    private static final int CLEANUP_BATCH = 200;

    private final MediaRetentionRepository retention;
    private final MediaBinaryReplicationRepository replication;
    private final MediaReferenceRepository references;
    private final PlatformMetrics metrics;

    public MediaCleanupScheduler(MediaRetentionRepository retention,
                                  MediaBinaryReplicationRepository replication,
                                  MediaReferenceRepository references,
                                  PlatformMetrics metrics) {
        this.retention = retention;
        this.replication = replication;
        this.references = references;
        this.metrics = metrics;
    }

    /**
     * Tick cleanup: giải phóng các hold đã đến hạn.
     *
     * <p>Lỗi trên một hold không làm dừng batch; toàn bộ sẽ retry ở
     * tick kế tiếp.</p>
     */
    @Scheduled(fixedDelayString = "${familya.media.cleanup.interval-ms:60000}")
    public void cleanup() {
        Instant now = Instant.now();
        // Lấy tối đa CLEANUP_BATCH (200) hold đến hạn trong lần này.
        var holds = retention.listReadyForCleanup(now, CLEANUP_BATCH);
        if (holds.isEmpty()) return;
        LOG.info("Media cleanup tick holds={}", holds.size());
        for (var hold : holds) {
            try {
                // Bước 1: giải phóng hold; cleanup worker bên ngoài sẽ
                // quan sát row đã release để xóa binary.
                retention.release(hold.id(), now);
                metrics.cleanupReleased("media-service");
                LOG.info("Released retention hold mediaId={} tree={}", hold.mediaId(), hold.treeId());
            } catch (Exception ex) {
                // Bước 2: log + tiếp tục; không ném ra ngoài để tránh
                // Spring dừng scheduler.
                LOG.warn("Cleanup failed for hold {}", hold.id(), ex);
            }
        }
    }

    /**
     * Ghi nhận yêu cầu replicate binary giữa hai region.
     *
     * @param mediaId UUID media cần replicate.
     * @param source  region nguồn.
     * @param target  region đích.
     * @param sha256  SHA-256 hash để đối chiếu sau replicate.
     * @return UUID row replicate vừa ghi (cho phép caller truy vết).
     */
    public UUID enqueueReplication(UUID mediaId, String source, String target, String sha256) {
        UUID rowId = UUID.randomUUID();
        replication.record(mediaId, source, target, sha256, "PENDING", Instant.now());
        return rowId;
    }

    /**
     * Xóa toàn bộ tham chiếu của một media.
     *
     * <p>Dùng khi media bị xóa vĩnh viễn và cần dọn tham chiếu
     * downstream.</p>
     *
     * @param mediaId UUID media cần purge tham chiếu.
     */
    public void purgeReferences(UUID mediaId) {
        var existing = references.listForMedia(mediaId);
        for (var ref : existing) {
            references.clear(mediaId, ref.targetKind(), ref.targetId());
        }
    }
}
