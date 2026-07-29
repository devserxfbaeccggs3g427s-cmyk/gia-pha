package com.familya.media.application.usecase;

import com.familya.media.application.port.in.ScanAndPromoteCommand;
import com.familya.media.application.port.out.MediaAuthorization;
import com.familya.media.application.port.out.MediaBinaryReplicationRepository;
import com.familya.media.application.port.out.MediaChangePublisher;
import com.familya.media.application.port.out.MediaRepository;
import com.familya.media.domain.event.MediaActivated;
import com.familya.media.domain.event.MediaScanned;
import com.familya.media.domain.exception.MediaNotFoundException;
import com.familya.media.domain.exception.ScannerUnavailableException;
import com.familya.media.domain.model.MediaAsset;
import com.familya.media.domain.model.MediaAsset.Status;
import com.familya.media.domain.model.ScannerResult;
import com.familya.media.application.port.out.ScannerGateway;
import com.familya.platform.error.ForbiddenException;
import com.familya.platform.error.OptimisticConcurrencyException;
import com.familya.platform.outbox.OutboxWriter;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class ScanAndPromoteUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(ScanAndPromoteUseCase.class);

    private final MediaRepository repo;
    private final ScannerGateway scanner;
    private final MediaAuthorization authz;
    private final MediaChangePublisher publisher;
    private final MediaBinaryReplicationRepository replication;
    private final OutboxWriter outbox;
    private final PlatformMetrics metrics;

    public ScanAndPromoteUseCase(MediaRepository repo,
                                  ScannerGateway scanner,
                                  MediaAuthorization authz,
                                  MediaChangePublisher publisher,
                                  MediaBinaryReplicationRepository replication,
                                  OutboxWriter outbox,
                                  PlatformMetrics metrics) {
        this.repo = repo;
        this.scanner = scanner;
        this.authz = authz;
        this.publisher = publisher;
        this.replication = replication;
        this.outbox = outbox;
        this.metrics = metrics;
    }

    @Transactional
    public void execute(ScanAndPromoteCommand cmd) {
        metrics.mutationAccepted("media-service", "scanAndPromote");
        MediaAsset asset = repo.findById(cmd.mediaId())
                .orElseThrow(() -> new MediaNotFoundException("Media " + cmd.mediaId() + " not found"));
        MediaAuthorization.Decision d = authz.authorize(asset.treeId(), cmd.actingUser(), cmd.expectedTreeRevision());
        if (!d.isAllowed()) {
            throw new ForbiddenException("Cannot scan/promote media: " + d.reason());
        }
        if (asset.version() != cmd.expectedVersion()) {
            throw new OptimisticConcurrencyException(
                    "Media " + cmd.mediaId() + " expected version " + cmd.expectedVersion() + " but found " + asset.version());
        }
        if (asset.status() != Status.QUARANTINED && asset.status() != Status.SCANNING) {
            throw new IllegalStateException("Media " + cmd.mediaId() + " is not in a scannable state (" + asset.status() + ")");
        }
        Instant now = Instant.now();
        ScannerResult result = scanner.scan(cmd.mediaId(), asset.quarantinePath(), asset.sha256(),
                asset.mimeType(), asset.byteSize());
        if (result.outcome() == ScannerResult.Outcome.FAILED) {
            repo.markFailed(cmd.mediaId(), asset.version(), now, result.evidence());
            publisher.publish(new MediaScanned(asset.treeId(), cmd.mediaId(), asset.version() + 1, now, "FAILED"));
            throw new ScannerUnavailableException("Scanner unavailable: " + result.evidence());
        }
        if (result.outcome() == ScannerResult.Outcome.INFECTED) {
            repo.markFailed(cmd.mediaId(), asset.version(), now, result.evidence());
            publisher.publish(new MediaScanned(asset.treeId(), cmd.mediaId(), asset.version() + 1, now, "INFECTED"));
            return;
        }
        repo.markReady(cmd.mediaId(), asset.version(), now);
        publisher.publish(new MediaScanned(asset.treeId(), cmd.mediaId(), asset.version() + 1, now, "CLEAN"));
        publisher.publish(new MediaActivated(asset.treeId(), cmd.mediaId(), asset.version() + 2, now, now));
        replication.record(cmd.mediaId(), "primary", "secondary", asset.sha256(), "PENDING", now);
        LOG.info("Promoted media mediaId={} tree={}", cmd.mediaId(), asset.treeId());
    }
}
