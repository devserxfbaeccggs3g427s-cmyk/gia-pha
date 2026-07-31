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

    @Transactional
    public Result execute(CreateUploadIntentCommand cmd) {
        metrics.mutationAccepted("media-service", "createUploadIntent");
        if (cmd.byteSize() > maxByteSize) {
            throw new UploadTooLargeException(
                    "Upload exceeds limit of " + maxByteSize + " bytes (got " + cmd.byteSize() + ")");
        }
        MediaAuthorization.Decision d = authz.authorize(cmd.treeId(), cmd.actingUser(), cmd.expectedTreeRevision());
        if (!d.isAllowed()) {
            throw new ForbiddenException("Cannot create upload intent: " + d.reason());
        }
        UUID mediaId = UUID.randomUUID();
        Instant now = Instant.now();
        String path = buildPath(cmd.treeId(), mediaId);
        MediaAsset asset = new MediaAsset(
                mediaId, cmd.treeId(), null, cmd.actingUser(),
                classifyKind(cmd.mimeType()), cmd.mimeType(),
                cmd.byteSize(), cmd.sha256(), cmd.originalFilename(),
                Status.QUARANTINED, path, false, null, null, now, now, 0L);
        repo.insert(asset);
        BlobCapabilityIssuer.Capability capability = issuer.issue(cmd.treeId(), mediaId, path, cmd.mimeType());
        publisher.publish(new MediaQuarantined(cmd.treeId(), mediaId, 1L, now, "INTENT_OPENED", "awaiting upload"));
        LOG.info("Created upload intent mediaId={} tree={} actingUser={} path={}",
                mediaId, cmd.treeId(), cmd.actingUser(), path);
        return new Result(mediaId, path, capability.signedPutUrl(), capability.expiresAtEpochMs());
    }

    private static Kind classifyKind(String mime) {
        if (mime == null) return Kind.OTHER;
        if (mime.startsWith("image/")) return Kind.PHOTO;
        if (mime.startsWith("video/")) return Kind.VIDEO;
        if (mime.startsWith("audio/")) return Kind.AUDIO;
        if (mime.startsWith("application/pdf") || mime.startsWith("text/")) return Kind.DOCUMENT;
        return Kind.OTHER;
    }

    private static String buildPath(UUID treeId, UUID mediaId) {
        return "quarantine/" + treeId + "/" + mediaId;
    }

    public record Result(UUID mediaId, String exactPath, String signedPutUrl, long expiresAtEpochMs) { }
}
