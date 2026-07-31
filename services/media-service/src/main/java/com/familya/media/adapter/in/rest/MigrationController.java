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
 * Adapter REST đầu vào (inbound) — Controller nội bộ phục vụ di trú (migration).
 * <p>
 * Cung cấp hai endpoint:
 * <ul>
 *   <li>{@code POST /api/v2/internal/media/migration/media} — nạp (load) một media
 *       từ manifest, idempotent: replay cùng {@code mediaId} không tạo bản ghi trùng.</li>
 *   <li>{@code GET /api/v2/internal/media/migration/reconcile} — đối soát các media
 *       được cập nhật kể từ một watermark (epoch ms) để hỗ trợ backfill/reconcile.</li>
 * </ul>
 * Endpoint chỉ dành cho internal — không có auth filter mặc định, giả định
 * upstream gateway chặn ở perimeter.
 */
@RestController
@RequestMapping(path = "/api/v2/internal/media", produces = MediaType.APPLICATION_JSON_VALUE)
public class MigrationController {

    private final MediaRepository repo;
    private final MediaWatermarkRepository watermark;

    /**
     * Khởi tạo controller.
     *
     * @param repo      repository cho {@code media_asset}.
     * @param watermark repository cho watermark theo (treeId, domain).
     */
    public MigrationController(MediaRepository repo, MediaWatermarkRepository watermark) {
        this.repo = repo;
        this.watermark = watermark;
    }

    /**
     * {@code POST /api/v2/internal/media/migration/media}.
     * <p>
     * Nạp một media từ manifest migration.
     * <ul>
     *   <li>Idempotent: nếu mediaId đã tồn tại thì trả 202 luôn mà không ghi đè.</li>
     *   <li>Watermark được advance (dùng GREATEST) sau khi insert.</li>
     *   <li>Headers: {@code X-Correlation-Id} bắt buộc để truy vết manifest.</li>
     *   <li>Body: {@link LoadMediaRequest}.</li>
     *   <li>Phản hồi: 202 + AsyncOperation.</li>
     * </ul>
     *
     * @param correlationId header truy vết manifest.
     * @param req           payload media cần nạp.
     * @return ResponseEntity với AsyncOperation.
     */
    @PostMapping(path = "/migration/media", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AsyncOperation> loadMedia(@RequestHeader("X-Correlation-Id") String correlationId,
                                                      @Valid @RequestBody LoadMediaRequest req) {
        // Idempotency: nếu mediaId đã có thì trả 202 để caller biết đã được xử lý trước đó.
        var existing = repo.findById(req.mediaId());
        if (existing.isPresent()) {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(AsyncOperation.accepted(req.mediaId(), "/api/v2/operations/" + req.mediaId()));
        }
        // Khởi tạo domain model — dùng Instant.now() nếu manifest thiếu createdAt/updatedAt.
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
        // Advance watermark dùng GREATEST nên không sợ giảm nếu manifest cũ được replay.
        watermark.advance(req.treeId(), "media", req.watermark() == null ? 0L : req.watermark(), Instant.now());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(AsyncOperation.accepted(req.mediaId(), "/api/v2/operations/" + req.mediaId()));
    }

    /**
     * {@code GET /api/v2/internal/media/migration/reconcile}.
     * <p>
     * Trả về danh sách media đã được cập nhật kể từ {@code watermark} (epoch ms)
     * phục vụ công cụ đối soát/reconcile bên ngoài.
     *
     * @param watermarkMs epoch ms — danh giới dưới.
     * @param limit       số lượng tối đa (mặc định 500).
     * @return ResponseEntity chứa count và danh sách mediaId.
     */
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
