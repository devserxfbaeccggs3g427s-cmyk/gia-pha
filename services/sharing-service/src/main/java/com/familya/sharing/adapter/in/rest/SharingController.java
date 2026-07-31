package com.familya.sharing.adapter.in.rest;

import com.familya.sharing.application.port.in.CreateShareLinkCommand;
import com.familya.sharing.application.port.in.RevokeShareLinkCommand;
import com.familya.sharing.application.port.out.ShareLinkRepository;
import com.familya.sharing.application.usecase.CreateShareLinkUseCase;
import com.familya.sharing.application.usecase.RevokeShareLinkUseCase;
import com.familya.sharing.domain.model.ShareLink;
import com.familya.platform.api.AsyncOperation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST controller chính phục vụ các thao tác quản lý liên kết chia sẻ
 * (share links) cho người dùng đã xác thực.
 * <p>
 * Các endpoint chính:
 * <ul>
 *     <li>{@code POST /api/v2/sharing/links} &mdash; tạo liên kết mới.</li>
 *     <li>{@code GET /api/v2/sharing/links?treeId=...} &mdash; liệt kê liên kết của cây.</li>
 *     <li>{@code DELETE /api/v2/sharing/links/{shareId}} &mdash; thu hồi liên kết.</li>
 * </ul>
 * Controller sử dụng các use case ở tầng ứng dụng và trả về {@link AsyncOperation}
 * cho các thao tác ghi (write) để client có thể theo dõi trạng thái xử lý.
 */
@RestController
@RequestMapping(path = "/api/v2/sharing", produces = MediaType.APPLICATION_JSON_VALUE)
public class SharingController {

    private final CreateShareLinkUseCase createShareLink;
    private final RevokeShareLinkUseCase revokeShareLink;
    private final ShareLinkRepository repo;

    /**
     * Khởi tạo controller với các use case cần thiết.
     *
     * @param createShareLink use case tạo liên kết.
     * @param revokeShareLink use case thu hồi liên kết.
     * @param repo            kho lưu trữ liên kết (dùng cho truy vấn).
     */
    public SharingController(CreateShareLinkUseCase createShareLink,
                              RevokeShareLinkUseCase revokeShareLink,
                              ShareLinkRepository repo) {
        this.createShareLink = createShareLink;
        this.revokeShareLink = revokeShareLink;
        this.repo = repo;
    }

    /**
     * Tạo một liên kết chia sẻ mới.
     *
     * @param actingUser           định danh người dùng thực hiện (header {@code X-Acting-User}).
     * @param expectedTreeRevision phiên bản kỳ vọng của cây (header {@code X-Tree-Revision}, tùy chọn).
     * @param req                  payload yêu cầu {@link CreateLinkRequest}.
     * @return {@link ResponseEntity} 201 Created với {@link ShareLinkResponse} chứa token mới.
     */
    @PostMapping(path = "/links", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ShareLinkResponse> create(@RequestHeader("X-Acting-User") UUID actingUser,
                                                     @RequestHeader(value = "X-Tree-Revision", required = false) Long expectedTreeRevision,
                                                     @Valid @RequestBody CreateLinkRequest req) {
        // Bước 1: Chuẩn hóa các tham số tùy chọn &mdash; null thành giá trị mặc định (0).
        long er = expectedTreeRevision == null ? 0L : expectedTreeRevision;
        Instant expiresAt = req.expiresAtEpochMs() == null ? null : Instant.ofEpochMilli(req.expiresAtEpochMs());

        // Bước 2: Gọi use case tạo liên kết.
        CreateShareLinkUseCase.Result r = createShareLink.execute(new CreateShareLinkCommand(
                req.treeId(), actingUser, er, req.scope(), req.targetId(), req.role(), expiresAt));

        // Bước 3: Trả về 201 Created với response chuẩn.
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ShareLinkResponse(r.shareId(), r.token(), r.expiresAt()));
    }

    /**
     * Liệt kê các liên kết chia sẻ của một cây.
     *
     * @param actingUser định danh người dùng (header {@code X-Acting-User}).
     * @param treeId     định danh cây cần truy vấn (query param).
     * @return danh sách {@link ShareLinkView} &mdash; mỗi phần tử chứa thông tin
     *         hiển thị (không bao gồm hash token).
     */
    @GetMapping("/links")
    public ResponseEntity<List<ShareLinkView>> list(@RequestHeader("X-Acting-User") UUID actingUser,
                                                     @RequestParam("treeId") UUID treeId) {
        // Bước 1: Lấy tất cả liên kết của cây rồi ánh xạ sang view DTO.
        var rows = repo.listByTree(treeId).stream()
                .map(l -> new ShareLinkView(l.id(), l.scope().name(), l.targetId(), l.role().name(),
                        l.createdAt(), l.expiresAt(), l.revokedAt(), l.revocationReason()))
                .toList();
        return ResponseEntity.ok(rows);
    }

    /**
     * Thu hồi một liên kết chia sẻ.
     *
     * @param actingUser           định danh người dùng (header {@code X-Acting-User}).
     * @param shareId              định danh liên kết cần thu hồi (path variable).
     * @param expectedVersion      phiên bản kỳ vọng của liên kết (header {@code If-Match}, tùy chọn).
     * @param expectedTreeRevision phiên bản kỳ vọng của cây (header {@code X-Tree-Revision}, tùy chọn).
     * @param reason               lý do thu hồi (query param, tùy chọn).
     * @return {@link ResponseEntity} 202 Accepted với {@link AsyncOperation} &mdash;
     *         client có thể theo dõi trạng thái qua URL trả về.
     */
    @DeleteMapping("/links/{shareId}")
    public ResponseEntity<AsyncOperation> revoke(@RequestHeader("X-Acting-User") UUID actingUser,
                                                  @PathVariable UUID shareId,
                                                  @RequestHeader(value = "If-Match", required = false) Long expectedVersion,
                                                  @RequestHeader(value = "X-Tree-Revision", required = false) Long expectedTreeRevision,
                                                  @RequestParam(value = "reason", required = false) String reason) {
        // Bước 1: Chuẩn hóa các tham số tùy chọn.
        long ev = expectedVersion == null ? 0L : expectedVersion;
        long er = expectedTreeRevision == null ? 0L : expectedTreeRevision;

        // Bước 2: Thực thi use case thu hồi (xử lý đồng bộ trong transaction).
        revokeShareLink.execute(new RevokeShareLinkCommand(shareId, actingUser, ev, er, reason));

        // Bước 3: Trả về AsyncOperation chuẩn để client tra cứu trạng thái.
        return ResponseEntity.accepted().body(AsyncOperation.accepted(shareId, "/api/v2/operations/" + shareId));
    }

    /**
     * Payload yêu cầu tạo liên kết &mdash; được validate bằng Bean Validation.
     *
     * @param treeId          định danh cây gia phả (bắt buộc).
     * @param scope           phạm vi chia sẻ (chuỗi không rỗng).
     * @param targetId        định danh mục tiêu (tùy chọn, {@code null} cho {@code TREE}).
     * @param role            vai trò (chuỗi, tùy chọn &mdash; nếu null dùng {@code VIEWER}).
     * @param expiresAtEpochMs thời điểm hết hạn tính bằng epoch ms (tùy chọn).
     */
    public record CreateLinkRequest(
            @jakarta.validation.constraints.NotNull UUID treeId,
            @NotBlank String scope,
            UUID targetId,
            String role,
            Long expiresAtEpochMs) { }

    /**
     * Phản hồi khi tạo liên kết thành công.
     *
     * @param shareId   định danh liên kết.
     * @param token     token thô &mdash; chỉ trả về đúng một lần.
     * @param expiresAt thời điểm hết hạn.
     */
    public record ShareLinkResponse(UUID shareId, String token, Instant expiresAt) { }

    /**
     * View thông tin liên kết dùng cho danh sách &mdash; không bao gồm hash token.
     *
     * @param shareId           định danh liên kết.
     * @param scope             phạm vi chia sẻ (dạng chuỗi).
     * @param targetId          định danh mục tiêu.
     * @param role              vai trò (dạng chuỗi).
     * @param createdAt         thời điểm tạo.
     * @param expiresAt         thời điểm hết hạn.
     * @param revokedAt         thời điểm thu hồi.
     * @param reason            lý do thu hồi.
     */
    public record ShareLinkView(UUID shareId, String scope, UUID targetId, String role,
                                 Instant createdAt, Instant expiresAt, Instant revokedAt, String reason) { }
}