package com.familya.media.adapter.in.rest;

import com.familya.media.adapter.out.persistence.JdbcOperationAuditWriter;
import com.familya.media.application.port.in.AssociateMediaCommand;
import com.familya.media.application.port.in.CreateAlbumCommand;
import com.familya.media.application.port.in.CreateUploadIntentCommand;
import com.familya.media.application.port.in.ScanAndPromoteCommand;
import com.familya.media.application.port.in.TombstoneMediaCommand;
import com.familya.media.application.port.in.VerifyUploadedCommand;
import com.familya.media.application.usecase.AssociateMediaUseCase;
import com.familya.media.application.usecase.CreateAlbumUseCase;
import com.familya.media.application.usecase.CreateUploadIntentUseCase;
import com.familya.media.application.usecase.ScanAndPromoteUseCase;
import com.familya.media.application.usecase.TombstoneMediaUseCase;
import com.familya.media.application.usecase.VerifyUploadedUseCase;
import com.familya.platform.api.AsyncOperation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api/v2/trees/{treeId}/media", produces = MediaType.APPLICATION_JSON_VALUE)
public class MediaController {

    private final CreateUploadIntentUseCase createUploadIntent;
    private final VerifyUploadedUseCase verifyUploaded;
    private final ScanAndPromoteUseCase scanAndPromote;
    private final TombstoneMediaUseCase tombstoneMedia;
    private final AssociateMediaUseCase associateMedia;
    private final CreateAlbumUseCase createAlbum;
    private final JdbcOperationAuditWriter operationWriter;

    public MediaController(CreateUploadIntentUseCase createUploadIntent,
                           VerifyUploadedUseCase verifyUploaded,
                           ScanAndPromoteUseCase scanAndPromote,
                           TombstoneMediaUseCase tombstoneMedia,
                           AssociateMediaUseCase associateMedia,
                           CreateAlbumUseCase createAlbum,
                           JdbcOperationAuditWriter operationWriter) {
        this.createUploadIntent = createUploadIntent;
        this.verifyUploaded = verifyUploaded;
        this.scanAndPromote = scanAndPromote;
        this.tombstoneMedia = tombstoneMedia;
        this.associateMedia = associateMedia;
        this.createAlbum = createAlbum;
        this.operationWriter = operationWriter;
    }

    @PostMapping("/uploads")
    @Transactional
    public ResponseEntity<UploadIntentResponse> createUploadIntent(
            @RequestHeader("X-Acting-User") UUID actingUser,
            @PathVariable UUID treeId,
            @RequestHeader(value = "X-Tree-Revision", required = false) Long expectedTreeRevision,
            @Valid @RequestBody CreateUploadIntentRequest req) {
        long er = expectedTreeRevision == null ? 0L : expectedTreeRevision;
        CreateUploadIntentUseCase.Result r = createUploadIntent.execute(new CreateUploadIntentCommand(
                treeId, actingUser, er, req.mimeType(), req.byteSize(),
                req.originalFilename(), req.sha256(), req.clientStartedAt()));
        operationWriter.record(r.mediaId(), r.mediaId(), treeId, actingUser,
                "media.createUploadIntent", "media", r.mediaId().toString(),
                "PENDING", Instant.now());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .header("Location", "/api/v2/trees/" + treeId + "/media/" + r.mediaId())
                .body(new UploadIntentResponse(r.mediaId(), r.exactPath(), r.signedPutUrl(), r.expiresAtEpochMs()));
    }

    @PostMapping("/{mediaId}/verify")
    @Transactional
    public ResponseEntity<AsyncOperation> verify(@RequestHeader("X-Acting-User") UUID actingUser,
                                                 @PathVariable UUID treeId,
                                                 @PathVariable UUID mediaId,
                                                 @RequestHeader(value = "If-Match", required = false) Long expectedVersion,
                                                 @RequestHeader(value = "X-Tree-Revision", required = false) Long expectedTreeRevision,
                                                 @Valid @RequestBody VerifyUploadRequest req) {
        long ev = expectedVersion == null ? 0L : expectedVersion;
        long er = expectedTreeRevision == null ? 0L : expectedTreeRevision;
        verifyUploaded.execute(new VerifyUploadedCommand(mediaId, actingUser, ev, er, req.sha256(), req.byteSize()));
        operationWriter.record(mediaId, mediaId, treeId, actingUser,
                "media.verifyUploaded", "media", mediaId.toString(),
                "PENDING", Instant.now());
        return ResponseEntity.accepted().body(AsyncOperation.accepted(mediaId, "/api/v2/operations/" + mediaId));
    }

    @PostMapping("/{mediaId}/scan")
    @Transactional
    public ResponseEntity<AsyncOperation> scan(@RequestHeader("X-Acting-User") UUID actingUser,
                                                @PathVariable UUID treeId,
                                                @PathVariable UUID mediaId,
                                                @RequestHeader(value = "If-Match", required = false) Long expectedVersion,
                                                @RequestHeader(value = "X-Tree-Revision", required = false) Long expectedTreeRevision) {
        long ev = expectedVersion == null ? 0L : expectedVersion;
        long er = expectedTreeRevision == null ? 0L : expectedTreeRevision;
        scanAndPromote.execute(new ScanAndPromoteCommand(mediaId, actingUser, ev, er));
        operationWriter.record(mediaId, mediaId, treeId, actingUser,
                "media.scanAndPromote", "media", mediaId.toString(),
                "PENDING", Instant.now());
        return ResponseEntity.accepted().body(AsyncOperation.accepted(mediaId, "/api/v2/operations/" + mediaId));
    }

    @PostMapping("/{mediaId}/associate")
    @Transactional
    public ResponseEntity<AsyncOperation> associate(@RequestHeader("X-Acting-User") UUID actingUser,
                                                    @PathVariable UUID treeId,
                                                    @PathVariable UUID mediaId,
                                                    @RequestHeader(value = "If-Match", required = false) Long expectedVersion,
                                                    @RequestHeader(value = "X-Tree-Revision", required = false) Long expectedTreeRevision,
                                                    @Valid @RequestBody AssociateRequest req) {
        long ev = expectedVersion == null ? 0L : expectedVersion;
        long er = expectedTreeRevision == null ? 0L : expectedTreeRevision;
        associateMedia.execute(new AssociateMediaCommand(mediaId, actingUser, ev, er, req.targetKind(), req.targetId()));
        operationWriter.record(mediaId, mediaId, treeId, actingUser,
                "media.associate", "media", mediaId.toString(),
                "PENDING", Instant.now());
        return ResponseEntity.accepted().body(AsyncOperation.accepted(mediaId, "/api/v2/operations/" + mediaId));
    }

    @DeleteMapping("/{mediaId}")
    @Transactional
    public ResponseEntity<AsyncOperation> tombstone(@RequestHeader("X-Acting-User") UUID actingUser,
                                                    @PathVariable UUID treeId,
                                                    @PathVariable UUID mediaId,
                                                    @RequestHeader(value = "If-Match", required = false) Long expectedVersion,
                                                    @RequestHeader(value = "X-Tree-Revision", required = false) Long expectedTreeRevision,
                                                    @RequestParam(value = "retentionHoldUntil", required = false) Long retentionEpochMs) {
        long ev = expectedVersion == null ? 0L : expectedVersion;
        long er = expectedTreeRevision == null ? 0L : expectedTreeRevision;
        Instant holdUntil = retentionEpochMs == null ? null : Instant.ofEpochMilli(retentionEpochMs);
        tombstoneMedia.execute(new TombstoneMediaCommand(mediaId, actingUser, ev, er, holdUntil));
        operationWriter.record(mediaId, mediaId, treeId, actingUser,
                "media.tombstone", "media", mediaId.toString(),
                "PENDING", Instant.now());
        return ResponseEntity.accepted().body(AsyncOperation.accepted(mediaId, "/api/v2/operations/" + mediaId));
    }

    @PostMapping("/albums")
    @Transactional
    public ResponseEntity<AsyncOperation> createAlbum(@RequestHeader("X-Acting-User") UUID actingUser,
                                                       @PathVariable UUID treeId,
                                                       @RequestHeader(value = "X-Tree-Revision", required = false) Long expectedTreeRevision,
                                                       @Valid @RequestBody CreateAlbumRequest req) {
        long er = expectedTreeRevision == null ? 0L : expectedTreeRevision;
        UUID id = createAlbum.execute(new CreateAlbumCommand(treeId, actingUser, er, req.name(), req.description(), req.coverMediaId()));
        operationWriter.record(id, id, treeId, actingUser,
                "media.createAlbum", "album", id.toString(),
                "PENDING", Instant.now());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .header("Location", "/api/v2/trees/" + treeId + "/media/albums/" + id)
                .body(AsyncOperation.accepted(id, "/api/v2/operations/" + id));
    }

    public record CreateUploadIntentRequest(
            @NotBlank String mimeType,
            @Positive long byteSize,
            String originalFilename,
            @NotBlank String sha256,
            Instant clientStartedAt) { }

    public record UploadIntentResponse(UUID mediaId, String exactPath, String signedPutUrl, long expiresAtEpochMs) { }

    public record VerifyUploadRequest(@NotBlank String sha256, @Positive long byteSize) { }

    public record AssociateRequest(@NotBlank String targetKind, @NotNull UUID targetId) { }

    public record CreateAlbumRequest(@NotBlank String name, String description, UUID coverMediaId) { }
}
