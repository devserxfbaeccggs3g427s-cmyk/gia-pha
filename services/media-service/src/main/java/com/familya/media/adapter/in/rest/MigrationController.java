package com.familya.media.adapter.in.rest;

import com.familya.media.application.port.out.MediaRepository;
import com.familya.media.application.port.out.MediaWatermarkRepository;
import com.familya.platform.api.AsyncOperation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Migration loader and reconciliation endpoints for the media
 * service. The loader is idempotent: replays of the same manifest
 * MUST NOT create duplicate assets or violate the unique SHA-256
 * constraint.
 */
@RestController
@RequestMapping(path = "/api/v2/internal/media", produces = MediaType.APPLICATION_JSON_VALUE)
public class MigrationController {

    private final MediaRepository repo;
    private final MediaWatermarkRepository watermark;

    public MigrationController(MediaRepository repo, MediaWatermarkRepository watermark) {
        this.repo = repo;
        this.watermark = watermark;
    }

    @PostMapping(path = "/migration/media", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AsyncOperation> loadMedia(@RequestHeader("X-Correlation-Id") String correlationId,
                                                      @Valid @RequestBody LoadMediaRequest req) {
        var existing = repo.findById(req.mediaId());
        if (existing.isPresent()) {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(AsyncOperation.accepted(req.mediaId(), "/api/v2/operations/" + req.mediaId()));
        }
        var asset = new com.familya.media.domain.model.MediaAsset(
                req.mediaId(), req.treeId(), req.albumId(), req.ownerUserId(),
                com.familya.media.domain.model.MediaAsset.Kind.valueOf(req.kind()),
                req.mimeType(), req.byteSize(), req.sha256(), req.originalFilename(),
                com.familya.media.domain.model.MediaAsset.Status.valueOf(req.status()),
                req.quarantinePath(), req.promoted(), req.retentionHoldUntil(),
                req.tombstonedAt(),
                req.createdAt() == null ? Instant.now() : req.createdAt(),
                req.updatedAt() == null ? Instant.now() : req.updatedAt(),
                0L);
        repo.insert(asset);
        watermark.advance(req.treeId(), "media", req.watermark() == null ? 0L : req.watermark(), Instant.now());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(AsyncOperation.accepted(req.mediaId(), "/api/v2/operations/" + req.mediaId()));
    }

    @GetMapping("/migration/reconcile")
    public ResponseEntity<ReconcileResponse> reconcile(@RequestParam("watermark") long watermarkMs,
                                                        @RequestParam(value = "limit", defaultValue = "500") int limit) {
        Instant from = Instant.ofEpochMilli(watermarkMs);
        var rows = repo.listAfter(from, limit);
        return ResponseEntity.ok(new ReconcileResponse(rows.size(), rows.stream().map(a -> a.id().toString()).toList()));
    }

    public record LoadMediaRequest(
            @NotNull UUID mediaId,
            @NotNull UUID treeId,
            UUID albumId,
            @NotNull UUID ownerUserId,
            @NotNull String kind,
            @NotNull String mimeType,
            long byteSize,
            @NotNull String sha256,
            String originalFilename,
            @NotNull String status,
            String quarantinePath,
            boolean promoted,
            Instant retentionHoldUntil,
            Instant tombstonedAt,
            Instant createdAt,
            Instant updatedAt,
            Long watermark,
            boolean replaySafe) { }

    public record ReconcileResponse(int count, List<String> mediaIds) { }
}
