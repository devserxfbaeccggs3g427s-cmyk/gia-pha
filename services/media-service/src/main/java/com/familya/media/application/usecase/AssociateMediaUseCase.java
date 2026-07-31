package com.familya.media.application.usecase;

import com.familya.media.application.port.in.AssociateMediaCommand;
import com.familya.media.application.port.out.MediaAuthorization;
import com.familya.media.application.port.out.MediaChangePublisher;
import com.familya.media.application.port.out.MediaReferenceRepository;
import com.familya.media.application.port.out.MediaRepository;
import com.familya.media.application.port.out.ReferenceAvailability;
import com.familya.media.domain.event.MediaAssociated;
import com.familya.media.domain.exception.MediaNotFoundException;
import com.familya.media.domain.exception.ReferenceUnavailableException;
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
public class AssociateMediaUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(AssociateMediaUseCase.class);

    private final MediaRepository repo;
    private final MediaReferenceRepository refs;
    private final ReferenceAvailability availability;
    private final MediaAuthorization authz;
    private final MediaChangePublisher publisher;
    private final PlatformMetrics metrics;

    public AssociateMediaUseCase(MediaRepository repo,
                                  MediaReferenceRepository refs,
                                  ReferenceAvailability availability,
                                  MediaAuthorization authz,
                                  MediaChangePublisher publisher,
                                  PlatformMetrics metrics) {
        this.repo = repo;
        this.refs = refs;
        this.availability = availability;
        this.authz = authz;
        this.publisher = publisher;
        this.metrics = metrics;
    }

    @Transactional
    public void execute(AssociateMediaCommand cmd) {
        metrics.mutationAccepted("media-service", "associate");
        MediaAsset asset = repo.findById(cmd.mediaId())
                .orElseThrow(() -> new MediaNotFoundException("Media " + cmd.mediaId() + " not found"));
        MediaAuthorization.Decision d = authz.authorize(asset.treeId(), cmd.actingUser(), cmd.expectedTreeRevision());
        if (!d.isAllowed()) {
            throw new ForbiddenException("Cannot associate media: " + d.reason());
        }
        if (asset.version() != cmd.expectedVersion()) {
            throw new OptimisticConcurrencyException(
                    "Media " + cmd.mediaId() + " expected version " + cmd.expectedVersion() + " but found " + asset.version());
        }
        if (asset.status() != Status.READY) {
            refs.upsert(cmd.mediaId(), asset.treeId(), cmd.targetKind(), cmd.targetId(),
                    "PENDING", Instant.now(), "media-not-ready");
            throw new ReferenceUnavailableException(
                    "Media " + cmd.mediaId() + " is not READY (status=" + asset.status() + ")");
        }
        boolean available = switch (cmd.targetKind()) {
            case "MEMBER" -> availability.isMemberAvailable(asset.treeId(), cmd.targetId());
            case "EVENT" -> availability.isEventAvailable(asset.treeId(), cmd.targetId());
            case "ALBUM" -> availability.isAlbumAvailable(asset.treeId(), cmd.targetId());
            default -> false;
        };
        if (!available) {
            refs.upsert(cmd.mediaId(), asset.treeId(), cmd.targetKind(), cmd.targetId(),
                    "PENDING", Instant.now(), "reference-unavailable");
            throw new ReferenceUnavailableException(
                    "Reference target " + cmd.targetKind() + ":" + cmd.targetId() + " not available for tree " + asset.treeId());
        }
        refs.upsert(cmd.mediaId(), asset.treeId(), cmd.targetKind(), cmd.targetId(),
                "ACTIVE", Instant.now(), null);
        publisher.publish(new MediaAssociated(asset.treeId(), cmd.mediaId(), asset.version() + 1,
                Instant.now(), cmd.targetKind(), cmd.targetId()));
        LOG.info("Associated media mediaId={} -> {}:{}", cmd.mediaId(), cmd.targetKind(), cmd.targetId());
    }
}
