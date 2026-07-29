package com.familya.media.adapter.in.blob;

import com.familya.media.application.port.out.BlobCapabilityIssuer;
import com.familya.media.application.port.out.MediaAuthorization;
import com.familya.media.application.port.out.MediaRepository;
import com.familya.platform.error.ForbiddenException;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

/**
 * The Blob Control Gateway adapter. Issues one-shot signed
 * capabilities for browser direct-to-quarantine uploads. The signed
 * URL is returned in the response body and never logged.
 */
@RestController
@RequestMapping(path = "/api/v2/media/blob", produces = MediaType.APPLICATION_JSON_VALUE)
public class BlobControlController {

    private static final Logger LOG = LoggerFactory.getLogger(BlobControlController.class);

    private final BlobCapabilityIssuer issuer;
    private final MediaAuthorization authz;
    private final MediaRepository repo;
    private final PlatformMetrics metrics;
    private final long maxByteSize;

    public BlobControlController(BlobCapabilityIssuer issuer, MediaAuthorization authz,
                                  MediaRepository repo, PlatformMetrics metrics,
                                  @Value("${familya.media.upload.max-byte-size:10485760}") long maxByteSize) {
        this.issuer = issuer;
        this.authz = authz;
        this.repo = repo;
        this.metrics = metrics;
        this.maxByteSize = maxByteSize;
    }

    @PostMapping(path = "/{mediaId}/capability", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CapabilityResponse> issueCapability(@RequestHeader("X-Acting-User") UUID actingUser,
                                                              @PathVariable UUID mediaId,
                                                              @RequestHeader(value = "X-Tree-Revision", required = false) Long expectedTreeRevision,
                                                              @RequestBody(required = false) CapabilityRequest req) {
        var asset = repo.findById(mediaId)
                .orElseThrow(() -> new ForbiddenException("Media " + mediaId + " not found"));
        MediaAuthorization.Decision d = authz.authorize(asset.treeId(), actingUser,
                expectedTreeRevision == null ? 0L : expectedTreeRevision);
        if (!d.isAllowed()) {
            metrics.capabilityIssued("media-service", "denied");
            throw new ForbiddenException("Cannot issue blob capability: " + d.reason());
        }
        if (asset.byteSize() > maxByteSize) {
            metrics.capabilityIssued("media-service", "denied-size");
            throw new IllegalArgumentException("Media exceeds size limit");
        }
        long ttl = req == null || req.ttlMs() == null ? 600_000L : req.ttlMs();
        BlobCapabilityIssuer.Capability capability = issuer.issue(asset.treeId(), mediaId, asset.quarantinePath(), asset.mimeType());
        metrics.capabilityIssued("media-service", "issued");
        LOG.info("Issued blob capability mediaId={} tree={} ttl={}ms", mediaId, asset.treeId(), ttl);
        return ResponseEntity.ok(new CapabilityResponse(
                mediaId, asset.quarantinePath(), capability.signedPutUrl(), capability.expiresAtEpochMs()));
    }

    @DeleteMapping("/{mediaId}/capability")
    public ResponseEntity<Void> invalidate(@PathVariable UUID mediaId) {
        issuer.invalidate(mediaId);
        return ResponseEntity.noContent().build();
    }

    public record CapabilityRequest(Long ttlMs) { }

    public record CapabilityResponse(UUID mediaId, String exactPath, String signedPutUrl, long expiresAtEpochMs) { }
}
