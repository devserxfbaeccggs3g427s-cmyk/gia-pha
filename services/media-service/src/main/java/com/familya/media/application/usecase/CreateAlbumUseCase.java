package com.familya.media.application.usecase;

import com.familya.media.application.port.in.CreateAlbumCommand;
import com.familya.media.application.port.out.AlbumRepository;
import com.familya.media.application.port.out.MediaAuthorization;
import com.familya.media.application.port.out.MediaChangePublisher;
import com.familya.media.domain.event.AlbumCreated;
import com.familya.media.domain.model.Album;
import com.familya.platform.error.ForbiddenException;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class CreateAlbumUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(CreateAlbumUseCase.class);

    private final AlbumRepository repo;
    private final MediaAuthorization authz;
    private final MediaChangePublisher publisher;
    private final PlatformMetrics metrics;

    public CreateAlbumUseCase(AlbumRepository repo, MediaAuthorization authz,
                              MediaChangePublisher publisher, PlatformMetrics metrics) {
        this.repo = repo;
        this.authz = authz;
        this.publisher = publisher;
        this.metrics = metrics;
    }

    @Transactional
    public UUID execute(CreateAlbumCommand cmd) {
        metrics.mutationAccepted("media-service", "createAlbum");
        MediaAuthorization.Decision d = authz.authorize(cmd.treeId(), cmd.actingUser(), cmd.expectedTreeRevision());
        if (!d.isAllowed()) {
            throw new ForbiddenException("Cannot create album: " + d.reason());
        }
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Album album = new Album(id, cmd.treeId(), cmd.name(), cmd.description(), cmd.coverMediaId(), now, now, 0L, null);
        repo.insert(album);
        publisher.publish(new AlbumCreated(cmd.treeId(), id, 1L, now, cmd.name()));
        LOG.info("Created album id={} tree={} name={}", id, cmd.treeId(), cmd.name());
        return id;
    }
}
