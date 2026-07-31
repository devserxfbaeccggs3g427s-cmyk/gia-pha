package com.familya.treeaccess.adapter.in.rest;

import com.familya.platform.api.AsyncOperation;
import com.familya.treeaccess.application.port.in.LoadTreeManifestCommand;
import com.familya.treeaccess.application.usecase.LoadTreeManifestUseCase;
import com.familya.treeaccess.application.usecase.ReconciliationUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST controller nội bộ phục vụ pipeline migration và đối chiếu (reconciliation).
 * Cung cấp hai endpoint:
 *
 * <ul>
 *   <li>{@code POST /api/v2/internal/tree-access/migration/trees} —
 *       nạp manifest cây trong quá trình cutover.</li>
 *   <li>{@code GET /api/v2/internal/tree-access/reconciliation/{treeId}} —
 *       sinh báo cáo đối chiếu cho một cây cụ thể.</li>
 * </ul>
 *
 * <p>Các endpoint này chỉ được dùng bởi các công cụ vận hành; lớp bảo mật mặc
 * định cho phép mọi yêu cầu nội bộ truy cập (xem {@code TreeAccessSecurityConfig}).</p>
 */
@RestController
@RequestMapping(path = "/api/v2/internal/tree-access", produces = MediaType.APPLICATION_JSON_VALUE)
public class MigrationController {

    /** Use-case nạp manifest cây (idempotent). */
    private final LoadTreeManifestUseCase loader;

    /** Use-case sinh báo cáo đối chiếu dùng cho cổng chặn cutover. */
    private final ReconciliationUseCase reconciliation;

    /**
     * Khởi tạo controller.
     *
     * @param loader        use-case nạp manifest
     * @param reconciliation use-case đối chiếu
     */
    public MigrationController(LoadTreeManifestUseCase loader, ReconciliationUseCase reconciliation) {
        this.loader = loader;
        this.reconciliation = reconciliation;
    }

    /**
     * Nạp manifest cây trong giai đoạn migration. Endpoint này idempotent — chạy
     * lại với cùng {@code treeId} trả về {@code DUPLICATE} mà không phá dữ liệu.
     *
     * @param correlationId mã tương quan truyền từ pipeline migration
     * @param req           payload yêu cầu đã validate
     * @return {@link AsyncOperation} với mã 202, cho biết đã chấp nhận xử lý
     */
    @PostMapping("/migration/trees")
    public ResponseEntity<AsyncOperation> load(@RequestHeader("X-Correlation-Id") String correlationId,
                                               @Valid @RequestBody LoadTreeManifestRequest req) {
        LoadTreeManifestUseCase.LoadResult r = loader.execute(new LoadTreeManifestCommand(
                req.treeId(), req.ownerUserId(), req.name(),
                req.initialRevision(), req.initialEpoch(), req.createdAt(),
                req.memberships().stream().map(m -> new LoadTreeManifestCommand.MembershipLine(
                        m.userId(), m.role(), m.grantedBy(), m.grantedAt())).toList(),
                req.replaySafe()));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(AsyncOperation.accepted(r.treeId(), "/api/v2/operations/" + r.treeId()));
    }

    /**
     * Trả báo cáo đối chiếu cho một cây, dùng làm cổng chặn cutover.
     *
     * @param treeId mã cây cần kiểm tra
     * @return báo cáo đối chiếu (có thể chứa danh sách sai lệch)
     */
    @GetMapping("/reconciliation/{treeId}")
    public ResponseEntity<ReconciliationUseCase.Report> reconcile(@PathVariable UUID treeId) {
        return ResponseEntity.ok(reconciliation.reconcile(treeId));
    }

    /** Yêu cầu nạp manifest — các trường bắt buộc được xác thực qua {@code jakarta.validation}. */
    public record LoadTreeManifestRequest(
            @NotNull UUID treeId,
            @NotNull UUID ownerUserId,
            @NotBlank String name,
            @NotNull Long initialRevision,
            @NotNull Long initialEpoch,
            @NotNull Instant createdAt,
            @NotNull List<MembershipLineDto> memberships,
            boolean replaySafe) { }

    /** Thành viên trong manifest — biểu diễn một dòng {@code tree_membership} lịch sử. */
    public record MembershipLineDto(
            @NotNull UUID userId,
            @NotBlank String role,
            @NotNull UUID grantedBy,
            @NotNull Instant grantedAt) { }
}