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

/**
 * Adapter REST đầu vào (inbound) — Controller chính cho media service.
 * <p>
 * Cung cấp các endpoint quản lý vòng đời media (upload intent → verify → scan
 * → associate / tombstone) và album trong một cây gia phả. Mọi endpoint đều
 * thuộc {@code @Transactional} và trả {@code 202 Accepted} cùng envelope
 * {@link com.familya.platform.api.AsyncOperation} để client poll trạng thái.
 * <p>
 * Xác thực: yêu cầu header {@code X-Acting-User} (UUID). Ủy quyền được xử lý
 * ở tầng use case thông qua {@code ProjectionMediaAuthorization}.
 */
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

    /**
     * Khởi tạo controller.
     *
     * @param createUploadIntent use case tạo upload intent.
     * @param verifyUploaded     use case xác minh upload.
     * @param scanAndPromote     use case scan và promote.
     * @param tombstoneMedia     use case tombstone media.
     * @param associateMedia     use case gắn media vào target.
     * @param createAlbum        use case tạo album.
     * @param operationWriter    writer ghi operation_audit cho API poll trạng thái.
     */
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

    /**
     * {@code POST /api/v2/trees/{treeId}/media/uploads}.
     * <p>
     * Tạo upload intent — trả về URL PUT đã ký để client upload thẳng lên blob store.
     * <ul>
     *   <li>Phương thức: POST</li>
     *   <li>Path: {@code /api/v2/trees/{treeId}/media/uploads}</li>
     *   <li>Headers yêu cầu: {@code X-Acting-User}, {@code X-Tree-Revision} (tùy chọn)</li>
     *   <li>Body: {@link CreateUploadIntentRequest}</li>
     *   <li>Phản hồi: {@code 202 Accepted} + {@link UploadIntentResponse} (mediaId, exactPath, signedPutUrl, expiresAtEpochMs)</li>
     *   <li>Side effect: ghi row PENDING vào operation_audit.</li>
     *   <li>Mã lỗi: 400 (validation), 403 (authz), 413 (size), 422 (validation use case).</li>
     * </ul>
     *
     * @param actingUser          UUID người thực hiện.
     * @param treeId              UUID cây.
     * @param expectedTreeRevision revision kỳ vọng của cây.
     * @param req                 payload yêu cầu.
     * @return ResponseEntity với UploadIntentResponse và header Location.
     */
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

    /**
     * {@code POST /api/v2/trees/{treeId}/media/{mediaId}/verify}.
     * <p>
     * Xác minh nội dung đã upload (so khớp SHA-256, kích thước).
     * <ul>
     *   <li>Headers: {@code X-Acting-User}, {@code If-Match} (expectedVersion, tùy chọn),
     *       {@code X-Tree-Revision} (tùy chọn).</li>
     *   <li>Body: {@link VerifyUploadRequest}.</li>
     *   <li>Phản hồi: {@code 202 Accepted} + AsyncOperation.</li>
     * </ul>
     *
     * @param actingUser          UUID người thực hiện.
     * @param treeId              UUID cây.
     * @param mediaId             UUID media.
     * @param expectedVersion     phiên bản kỳ vọng (optimistic lock).
     * @param expectedTreeRevision revision kỳ vọng của cây.
     * @param req                 payload xác minh.
     * @return ResponseEntity với AsyncOperation.
     */
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

    /**
     * {@code POST /api/v2/trees/{treeId}/media/{mediaId}/scan}.
     * <p>
     * Kích hoạt scanner và promote media nếu sạch.
     *
     * @param actingUser      UUID người thực hiện.
     * @param treeId          UUID cây.
     * @param mediaId         UUID media.
     * @param expectedVersion phiên bản kỳ vọng.
     * @param expectedTreeRevision revision kỳ vọng của cây.
     * @return ResponseEntity với AsyncOperation.
     */
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

    /**
     * {@code POST /api/v2/trees/{treeId}/media/{mediaId}/associate}.
     * <p>
     * Gắn media đã READY vào một target (member hoặc event).
     *
     * @param actingUser      UUID người thực hiện.
     * @param treeId          UUID cây.
     * @param mediaId         UUID media.
     * @param expectedVersion phiên bản kỳ vọng.
     * @param expectedTreeRevision revision kỳ vọng của cây.
     * @param req             payload chứa targetKind/targetId.
     * @return ResponseEntity với AsyncOperation.
     */
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

    /**
     * {@code DELETE /api/v2/trees/{treeId}/media/{mediaId}}.
     * <p>
     * Tombstone media (xóa mềm). Có thể truyền {@code retentionHoldUntil} (epoch ms)
     * để thiết lập retention hold theo yêu cầu pháp lý.
     *
     * @param actingUser         UUID người thực hiện.
     * @param treeId             UUID cây.
     * @param mediaId            UUID media.
     * @param expectedVersion    phiên bản kỳ vọng.
     * @param expectedTreeRevision revision kỳ vọng của cây.
     * @param retentionEpochMs   epoch ms đến khi được phép xóa vĩnh viễn (tùy chọn).
     * @return ResponseEntity với AsyncOperation.
     */
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

    /**
     * {@code POST /api/v2/trees/{treeId}/media/albums}.
     * <p>
     * Tạo album mới trong cây.
     *
     * @param actingUser          UUID người thực hiện.
     * @param treeId              UUID cây.
     * @param expectedTreeRevision revision kỳ vọng của cây.
     * @param req                 payload tạo album.
     * @return ResponseEntity với AsyncOperation và header Location.
     */
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
