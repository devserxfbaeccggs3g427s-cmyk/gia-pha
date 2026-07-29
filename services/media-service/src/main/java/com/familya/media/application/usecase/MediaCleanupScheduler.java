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
 * Delayed cleanup job. Tombstoned media and expired retention holds
 * are released when their deadline passes. Failures are logged; the
 * next tick re-runs the batch.
 */
@Component
public class MediaCleanupScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(MediaCleanupScheduler.class);

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

    @Scheduled(fixedDelayString = "${familya.media.cleanup.interval-ms:60000}")
    public void cleanup() {
        Instant now = Instant.now();
        var holds = retention.listReadyForCleanup(now, 200);
        if (holds.isEmpty()) return;
        LOG.info("Media cleanup tick holds={}", holds.size());
        for (var hold : holds) {
            try {
                retention.release(hold.id(), now);
                metrics.cleanupReleased("media-service");
                LOG.info("Released retention hold mediaId={} tree={}", hold.mediaId(), hold.treeId());
            } catch (Exception ex) {
                LOG.warn("Cleanup failed for hold {}", hold.id(), ex);
            }
        }
    }

    public UUID enqueueReplication(UUID mediaId, String source, String target, String sha256) {
        UUID rowId = UUID.randomUUID();
        replication.record(mediaId, source, target, sha256, "PENDING", Instant.now());
        return rowId;
    }

    public void purgeReferences(UUID mediaId) {
        var existing = references.listForMedia(mediaId);
        for (var ref : existing) {
            references.clear(mediaId, ref.targetKind(), ref.targetId());
        }
    }
}
