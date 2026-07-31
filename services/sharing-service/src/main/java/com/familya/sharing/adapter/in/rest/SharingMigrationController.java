package com.familya.sharing.adapter.in.rest;

import com.familya.sharing.application.port.in.CreateShareLinkCommand;
import com.familya.sharing.application.port.out.ShareLinkRepository;
import com.familya.sharing.application.usecase.CreateShareLinkUseCase;
import com.familya.sharing.application.usecase.RebuildPublicProjectionUseCase;
import com.familya.sharing.application.port.out.AllowlistedProjectionRepository.ShareScope;
import com.familya.platform.api.AsyncOperation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

/**
 * REST controller phục vụ các thao tác <b>di trú/nội bộ</b> (migration/internal)
 * của sharing-service &mdash; được sử dụng bởi các công cụ vận hành hoặc
 * migration từ hệ thống cũ.
 * <p>
 * Hai endpoint chính:
 * <ul>
 *     <li>{@code POST /api/v2/internal/sharing/migration/projection/rebuild}
 *         &mdash; tái tạo projection công khai cho một phạm vi/mục tiêu.</li>
 *     <li>{@code POST /api/v2/internal/sharing/migration/legacy-token}
 *         &mdash; nhập token cũ từ hệ thống legacy (idempotent).</li>
 * </ul>
 * Controller này thường được bảo vệ bởi các cơ chế bảo mật mạng (network ACL)
 * thay vì xác thực người dùng cuối.
 */
@RestController
@RequestMapping(path = "/api/v2/internal/sharing", produces = MediaType.APPLICATION_JSON_VALUE)
public class SharingMigrationController {

    private final RebuildPublicProjectionUseCase rebuild;
    private final CreateShareLinkUseCase createShareLink;
    private final ShareLinkRepository repo;

    /**
     * Khởi tạo controller.
     *
     * @param rebuild        use case tái tạo projection.
     * @param createShareLink use case tạo liên kết (dùng cho import legacy).
     * @param repo           kho lưu trữ liên kết (dùng để kiểm tra trùng lặp).
     */
    public SharingMigrationController(RebuildPublicProjectionUseCase rebuild,
                                       CreateShareLinkUseCase createShareLink,
                                       ShareLinkRepository repo) {
        this.rebuild = rebuild;
        this.createShareLink = createShareLink;
        this.repo = repo;
    }

    /**
     * Tái tạo projection công khai cho một phạm vi/mục tiêu.
     *
     * @param correlationId định danh tương quan (header {@code X-Correlation-Id}) &mdash;
     *                       dùng cho truy vết log.
     * @param req           payload {@link RebuildRequest}.
     * @return {@link ResponseEntity} 202 Accepted với {@link AsyncOperation}.
     */
    @PostMapping(path = "/migration/projection/rebuild", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AsyncOperation> rebuild(@RequestHeader("X-Correlation-Id") String correlationId,
                                                    @Valid @RequestBody RebuildRequest req) {
        // Bước 1: Thực thi use case (đồng bộ trong transaction).
        rebuild.execute(req.treeId(), ShareScope.valueOf(req.scope()), req.targetId(), req.watermark());

        // Bước 2: Trả về 202 Accepted &mdash; client theo dõi qua URL truy vấn.
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(AsyncOperation.accepted(req.treeId(), "/api/v2/operations/" + req.treeId()));
    }

    /**
     * Nhập token cũ từ hệ thống legacy.
     * <p>
     * Quy trình:
     * <ol>
     *     <li>Băm token legacy theo SHA-256.</li>
     *     <li>Nếu chưa tồn tại trong DB &mdash; tạo mới liên kết tương ứng.</li>
     *     <li>Trả về {@link AsyncOperation} để client theo dõi.</li>
     * </ol>
     * Phương thức này idempotent &mdash; gọi nhiều lần với cùng token sẽ không
     * tạo ra nhiều liên kết.
     *
     * @param correlationId định danh tương quan (header).
     * @param req           payload {@link LegacyTokenRequest}.
     * @return {@link ResponseEntity} 202 Accepted.
     */
    @PostMapping(path = "/migration/legacy-token", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AsyncOperation> importLegacy(@RequestHeader("X-Correlation-Id") String correlationId,
                                                         @Valid @RequestBody LegacyTokenRequest req) {
        // Bước 1: Băm token để tra cứu &mdash; chỉ hash được lưu trong DB.
        String hash = CreateShareLinkUseCase.sha256Hex(req.token());

        // Bước 2: Nếu chưa tồn tại thì tạo mới; nếu đã có thì bỏ qua (idempotent).
        if (repo.findByTokenHash(hash).isEmpty()) {
            createShareLink.execute(new CreateShareLinkCommand(
                    req.treeId(), req.actingUser(), 0L, req.scope(), req.targetId(), req.role(), req.expiresAt()));
        }

        // Bước 3: Trả về AsyncOperation.
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(AsyncOperation.accepted(req.treeId(), "/api/v2/operations/" + req.treeId()));
    }

    /**
     * Payload cho endpoint rebuild projection.
     *
     * @param treeId    định danh cây gia phả.
     * @param scope     phạm vi projection.
     * @param targetId  định danh mục tiêu (tùy chọn).
     * @param watermark phiên bản watermark mong muốn.
     */
    public record RebuildRequest(
            @NotNull UUID treeId,
            @NotNull String scope,
            UUID targetId,
            long watermark) { }

    /**
     * Payload cho endpoint nhập token legacy.
     *
     * @param treeId     định danh cây gia phả.
     * @param actingUser định danh người dùng thực hiện migration.
     * @param token      token thô từ hệ thống cũ.
     * @param scope      phạm vi chia sẻ.
     * @param targetId   định danh mục tiêu (tùy chọn).
     * @param role       vai trò (tùy chọn).
     * @param expiresAt  thời điểm hết hạn (tùy chọn).
     */
    public record LegacyTokenRequest(
            @NotNull UUID treeId,
            @NotNull UUID actingUser,
            @NotNull String token,
            @NotNull String scope,
            UUID targetId,
            String role,
            Instant expiresAt) { }
}