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

    @Transactional
    public void execute(VerifyUploadedCommand cmd) {
        metrics.mutationAccepted("media-service", "verifyUploaded");
        MediaAsset asset = repo.findById(cmd.mediaId())
                .orElseThrow(() -> new MediaNotFoundException("Media " + cmd.mediaId() + " not found"));
        MediaAuthorization.Decision d = authz.authorize(asset.treeId(), cmd.actingUser(), cmd.expectedTreeRevision());
        if (!d.isAllowed()) {
            throw new ForbiddenException("Cannot verify media: " + d.reason());
        }
        if (asset.version() != cmd.expectedVersion()) {
            throw new OptimisticConcurrencyException(
                    "Media " + cmd.mediaId() + " expected version " + cmd.expectedVersion() + " but found " + asset.version());
        }
        if (!asset.sha256().equalsIgnoreCase(cmd.sha256())) {
            repo.markFailed(cmd.mediaId(), asset.version(), Instant.now(), "sha256 mismatch");
            publisher.publish(new MediaQuarantined(asset.treeId(), cmd.mediaId(), asset.version() + 1,
                    Instant.now(), "VERIFICATION_FAILED", "sha256 mismatch"));
            throw new IllegalStateException("Uploaded sha256 does not match intent sha256 for media " + cmd.mediaId());
        }
        if (asset.byteSize() != cmd.byteSize()) {
            repo.markFailed(cmd.mediaId(), asset.version(), Instant.now(), "byte size mismatch");
            publisher.publish(new MediaQuarantined(asset.treeId(), cmd.mediaId(), asset.version() + 1,
                    Instant.now(), "VERIFICATION_FAILED", "byte size mismatch"));
            throw new IllegalStateException("Uploaded byte size does not match intent for media " + cmd.mediaId());
        }
        repo.markScanning(cmd.mediaId(), asset.version(), Instant.now());
        publisher.publish(new MediaQuarantined(asset.treeId(), cmd.mediaId(), asset.version() + 1,
                Instant.now(), "VERIFIED_AWAITING_SCAN", null));
        LOG.info("Verified upload mediaId={} tree={}", cmd.mediaId(), asset.treeId());
    }
}
