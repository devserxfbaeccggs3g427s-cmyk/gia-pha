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

@Service
public class TombstoneMediaUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(TombstoneMediaUseCase.class);

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

    @Transactional
    public void execute(TombstoneMediaCommand cmd) {
        metrics.mutationAccepted("media-service", "tombstone");
        MediaAsset asset = repo.findById(cmd.mediaId())
                .orElseThrow(() -> new MediaNotFoundException("Media " + cmd.mediaId() + " not found"));
        MediaAuthorization.Decision d = authz.authorize(asset.treeId(), cmd.actingUser(), cmd.expectedTreeRevision());
        if (!d.isAllowed()) {
            throw new ForbiddenException("Cannot tombstone media: " + d.reason());
        }
        if (asset.version() != cmd.expectedVersion()) {
            throw new OptimisticConcurrencyException(
                    "Media " + cmd.mediaId() + " expected version " + cmd.expectedVersion() + " but found " + asset.version());
        }
        Instant now = Instant.now();
        Instant holdUntil = cmd.retentionHoldUntil() == null
                ? now.plus(30, ChronoUnit.DAYS)
                : cmd.retentionHoldUntil();
        repo.tombstone(cmd.mediaId(), now, asset.version());
        retention.placeHold(cmd.mediaId(), asset.treeId(), holdUntil, "user-requested");
        publisher.publish(new MediaDetached(asset.treeId(), cmd.mediaId(), asset.version() + 1,
                now, "TOMBSTONE"));
        LOG.info("Tombstoned media mediaId={} tree={} holdUntil={}", cmd.mediaId(), asset.treeId(), holdUntil);
    }
}
