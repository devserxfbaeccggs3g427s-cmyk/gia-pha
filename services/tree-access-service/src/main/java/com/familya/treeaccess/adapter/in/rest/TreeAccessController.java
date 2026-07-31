package com.familya.treeaccess.adapter.in.rest;

import com.familya.platform.api.AsyncOperation;
import com.familya.treeaccess.application.port.in.AdvanceRevisionCommand;
import com.familya.treeaccess.application.port.in.CreateTreeCommand;
import com.familya.treeaccess.application.port.in.FreezeTreeCommand;
import com.familya.treeaccess.application.port.in.GrantMembershipCommand;
import com.familya.treeaccess.application.port.in.InitiateDeleteTreeCommand;
import com.familya.treeaccess.application.port.in.RevokeMembershipCommand;
import com.familya.treeaccess.application.port.in.TombstoneTreeCommand;
import com.familya.treeaccess.application.port.in.UnfreezeTreeCommand;
import com.familya.treeaccess.application.usecase.AuthorizeUseCase;
import com.familya.treeaccess.application.usecase.CreateTreeUseCase;
import com.familya.treeaccess.application.usecase.DeleteTreeSagaService;
import com.familya.treeaccess.application.usecase.GrantMembershipUseCase;
import com.familya.treeaccess.application.usecase.RevokeMembershipUseCase;
import com.familya.treeaccess.application.usecase.TreeLifecycleUseCases;
import com.familya.treeaccess.domain.model.TreeMembership;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller chính cho mọi thao tác trên cây gia phả và thành viên.
 * Hỗ trợ các nghiệp vụ: tạo cây, cấp/thu hồi quyền, đóng băng/bỏ đóng băng,
 * đánh dấu tombstone, khởi tạo Saga xóa cây, tăng revision và tra cứu
 * phân quyền thành viên.
 */
@RestController
@RequestMapping(path = "/api/v2/trees", produces = MediaType.APPLICATION_JSON_VALUE)
public class TreeAccessController {

    /** Use-case tạo cây mới. */
    private final CreateTreeUseCase createTree;

    /** Use-case cấp quyền thành viên. */
    private final GrantMembershipUseCase grantMembership;

    /** Use-case thu hồi quyền thành viên. */
    private final RevokeMembershipUseCase revokeMembership;

    /** Use-case vòng đời cây (freeze, unfreeze, tombstone, advanceRevision). */
    private final TreeLifecycleUseCases lifecycle;

    /** Use-case tra cứu phân quyền thành viên trên cây. */
    private final AuthorizeUseCase authorize;

    /** Service điều phối Saga xóa cây (delete-tree). */
    private final DeleteTreeSagaService deleteTreeSaga;

    /**
     * Khởi tạo controller với các use-case tương ứng.
     *
     * @param createTree       use-case tạo cây
     * @param grantMembership  use-case cấp quyền
     * @param revokeMembership use-case thu hồi quyền
     * @param lifecycle        use-case vòng đời cây
     * @param authorize        use-case tra cứu phân quyền
     * @param deleteTreeSaga   service Saga xóa cây
     */
    public TreeAccessController(CreateTreeUseCase createTree,
                                GrantMembershipUseCase grantMembership,
                                RevokeMembershipUseCase revokeMembership,
                                TreeLifecycleUseCases lifecycle,
                                AuthorizeUseCase authorize,
                                DeleteTreeSagaService deleteTreeSaga) {
        this.createTree = createTree;
        this.grantMembership = grantMembership;
        this.revokeMembership = revokeMembership;
        this.lifecycle = lifecycle;
        this.authorize = authorize;
        this.deleteTreeSaga = deleteTreeSaga;
    }

    /**
     * Tạo cây gia phả mới. Người dùng trong header {@code X-Acting-User} trở thành
     * chủ sở hữu và luôn có quyền ADMIN. Phản hồi 202 với {@link AsyncOperation}
     * để client có thể poll tiến trình.
     *
     * @param actingUser UUID người dùng thực hiện (sẽ là owner)
     * @param req        payload đã validate, chứa tên cây
     * @return {@link AsyncOperation} với mã 202 và header Location trỏ tới tài nguyên cây
     */
    @PostMapping
    public ResponseEntity<AsyncOperation> create(@RequestHeader("X-Acting-User") UUID actingUser,
                                                 @Valid @RequestBody CreateTreeRequest req) {
        UUID treeId = createTree.execute(new CreateTreeCommand(actingUser, req.name()));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .header("Location", "/api/v2/trees/" + treeId)
                .body(AsyncOperation.accepted(treeId, "/api/v2/operations/" + treeId));
    }

    /**
     * Cấp quyền thành viên cho người dùng trên một cây. Chỉ chủ cây hoặc
     * thành viên ADMIN mới có thể thực hiện.
     *
     * @param actingUser UUID người thực hiện
     * @param treeId     mã cây
     * @param req        payload chứa userId và role mới
     * @return mã 202 với {@link AsyncOperation} trỏ tới operationId theo dõi
     */
    @PostMapping("/{treeId}/memberships")
    public ResponseEntity<AsyncOperation> grant(@RequestHeader("X-Acting-User") UUID actingUser,
                                               @PathVariable UUID treeId,
                                               @Valid @RequestBody GrantRequest req) {
        UUID id = grantMembership.execute(new GrantMembershipCommand(treeId, req.userId(), req.role(), actingUser));
        return ResponseEntity.accepted().body(AsyncOperation.accepted(id, "/api/v2/operations/" + id));
    }

    /**
     * Thu hồi quyền thành viên. Không thể thu hồi chủ sở hữu cây (luật bất biến).
     *
     * @param actingUser UUID người thực hiện (phải có ADMIN)
     * @param treeId     mã cây
     * @param userId     mã người dùng bị thu hồi
     * @param reason     lý do thu hồi, mặc định {@code "manual"}
     * @return mã 202 với {@link AsyncOperation}
     */
    @DeleteMapping("/{treeId}/memberships/{userId}")
    public ResponseEntity<AsyncOperation> revoke(@RequestHeader("X-Acting-User") UUID actingUser,
                                                 @PathVariable UUID treeId,
                                                 @PathVariable UUID userId,
                                                 @RequestParam(required = false, defaultValue = "manual") String reason) {
        revokeMembership.execute(new RevokeMembershipCommand(treeId, userId, actingUser, reason));
        return ResponseEntity.accepted().body(AsyncOperation.accepted(UUID.randomUUID(),
                "/api/v2/operations/" + UUID.randomUUID()));
    }

    /**
     * Đóng băng cây — chặn mọi thao tác ghi. Có thể mở lại bằng {@link #unfreeze}.
     *
     * @param actingUser     UUID người thực hiện (phải có ADMIN)
     * @param treeId         mã cây
     * @param expectedVersion phiên bản kỳ vọng qua header {@code If-Match} (tuỳ chọn)
     * @return mã 202 với {@link AsyncOperation}
     */
    @PostMapping("/{treeId}/freeze")
    public ResponseEntity<AsyncOperation> freeze(@RequestHeader("X-Acting-User") UUID actingUser,
                                                 @PathVariable UUID treeId,
                                                 @RequestHeader(value = "If-Match", required = false) Long expectedVersion) {
        lifecycle.freeze(new FreezeTreeCommand(treeId, actingUser, expectedVersion == null ? 0L : expectedVersion));
        return ResponseEntity.accepted().body(AsyncOperation.accepted(UUID.randomUUID(), "/api/v2/operations/" + UUID.randomUUID()));
    }

    /**
     * Bỏ đóng băng — đưa cây về trạng thái ACTIVE.
     *
     * @param actingUser     UUID người thực hiện (phải có ADMIN)
     * @param treeId         mã cây
     * @param expectedVersion phiên bản kỳ vọng qua header {@code If-Match}
     * @return mã 202 với {@link AsyncOperation}
     */
    @PostMapping("/{treeId}/unfreeze")
    public ResponseEntity<AsyncOperation> unfreeze(@RequestHeader("X-Acting-User") UUID actingUser,
                                                   @PathVariable UUID treeId,
                                                   @RequestHeader(value = "If-Match", required = false) Long expectedVersion) {
        lifecycle.unfreeze(new UnfreezeTreeCommand(treeId, actingUser, expectedVersion == null ? 0L : expectedVersion));
        return ResponseEntity.accepted().body(AsyncOperation.accepted(UUID.randomUUID(), "/api/v2/operations/" + UUID.randomUUID()));
    }

    /**
     * Đánh dấu tombstone — đây là bước không thể thu hồi trong vòng đời cây.
     *
     * @param actingUser     UUID người thực hiện (phải có ADMIN)
     * @param treeId         mã cây
     * @param expectedVersion phiên bản kỳ vọng qua header {@code If-Match}
     * @return mã 202 với {@link AsyncOperation}
     */
    @PostMapping("/{treeId}/tombstone")
    public ResponseEntity<AsyncOperation> tombstone(@RequestHeader("X-Acting-User") UUID actingUser,
                                                    @PathVariable UUID treeId,
                                                    @RequestHeader(value = "If-Match", required = false) Long expectedVersion) {
        lifecycle.tombstone(new TombstoneTreeCommand(treeId, actingUser, expectedVersion == null ? 0L : expectedVersion));
        return ResponseEntity.accepted().body(AsyncOperation.accepted(UUID.randomUUID(), "/api/v2/operations/" + UUID.randomUUID()));
    }

    /**
     * Khởi tạo Saga xóa cây. Phản hồi HTTP mang {@code operationId} bền vững;
     * client sẽ poll {@code /api/v2/operations/{id}} để theo dõi tiến trình Saga.
     * Việc xoá nhị phân vật lý được hoãn lại cho worker dọn dẹp của Media service
     * và KHÔNG nằm trong rào chắn (barrier) của Saga này.
     *
     * @param actingUser        UUID người thực hiện (phải có ADMIN/owner)
     * @param treeId            mã cây
     * @param expectedVersion   phiên bản kỳ vọng (header {@code If-Match})
     * @param expectedEpoch     epoch kỳ vọng (header {@code X-Tree-Epoch})
     * @param placeRetentionHolds đặt retention hold hay không (mặc định {@code true})
     * @return mã 202 với {@link AsyncOperation} trỏ tới {@code operationId}
     */
    @DeleteMapping("/{treeId}")
    public ResponseEntity<AsyncOperation> deleteTree(@RequestHeader("X-Acting-User") UUID actingUser,
                                                      @PathVariable UUID treeId,
                                                      @RequestHeader(value = "If-Match", required = false) Long expectedVersion,
                                                      @RequestHeader(value = "X-Tree-Epoch", required = false) Long expectedEpoch,
                                                      @RequestParam(value = "placeRetentionHolds", required = false, defaultValue = "true") boolean placeRetentionHolds) {
        long ev = expectedVersion == null ? 0L : expectedVersion;
        long ee = expectedEpoch == null ? 0L : expectedEpoch;
        UUID operationId = deleteTreeSaga.initiate(new InitiateDeleteTreeCommand(
                treeId, actingUser, ev, ee, placeRetentionHolds));
        return ResponseEntity.accepted()
                .header("Location", "/api/v2/operations/" + operationId)
                .body(AsyncOperation.accepted(operationId, "/api/v2/operations/" + operationId));
    }

    /**
     * Tăng revision/epoch theo yêu cầu từ orchestrator. Chỉ ADMIN mới có quyền
     * gọi và cây phải đang ACTIVE.
     *
     * @param actingUser UUID người thực hiện
     * @param treeId     mã cây
     * @param req        payload chứa phiên bản kỳ vọng, revision/epoch mới và lý do
     * @return mã 202 với {@link AsyncOperation}
     */
    @PostMapping("/{treeId}/revisions")
    public ResponseEntity<AsyncOperation> advanceRevision(@RequestHeader("X-Acting-User") UUID actingUser,
                                                          @PathVariable UUID treeId,
                                                          @Valid @RequestBody AdvanceRevisionRequest req) {
        lifecycle.advanceRevision(new AdvanceRevisionCommand(treeId, actingUser,
                req.expectedVersion(), req.newRevision(), req.newEpoch(), req.reason()));
        return ResponseEntity.accepted().body(AsyncOperation.accepted(UUID.randomUUID(), "/api/v2/operations/" + UUID.randomUUID()));
    }

    /**
     * Tra cứu phân quyền của một người dùng trên cây. Nếu không tìm thấy projection
     * hoặc đã thu hồi, trả về 403.
     *
     * @param treeId           mã cây
     * @param userId           mã người dùng
     * @param expectedRevision revision tối thiểu mà caller yêu cầu
     * @return {@link AuthorizationResponse} với mã 200 hoặc 403 nếu không có quyền
     */
    @GetMapping("/{treeId}/authorizations/{userId}")
    public ResponseEntity<AuthorizationResponse> authorize(@PathVariable UUID treeId,
                                                           @PathVariable UUID userId,
                                                           @RequestParam(value = "expectedRevision", required = false) Long expectedRevision) {
        long er = expectedRevision == null ? 0L : expectedRevision;
        var projection = authorize.authorize(treeId, userId, er)
                .orElseThrow(() -> new com.familya.platform.error.ForbiddenException(
                        "User " + userId + " is not authorized on tree " + treeId));
        return ResponseEntity.ok(new AuthorizationResponse(
                projection.treeId(), projection.userId(),
                projection.role() == null ? null : projection.role().name(),
                projection.canEdit(), projection.canView(),
                projection.revision(), projection.epoch()));
    }

    /** Payload yêu cầu tạo cây — chỉ cần tên không rỗng. */
    public record CreateTreeRequest(@NotBlank String name) { }

    /** Payload yêu cầu cấp quyền — gồm userId và vai trò mới. */
    public record GrantRequest(@NotNull UUID userId, @NotNull TreeMembership.Role role) { }

    /** Payload yêu cầu tăng revision — gồm phiên bản kỳ vọng, revision/epoch mới và lý do. */
    public record AdvanceRevisionRequest(@NotNull Long expectedVersion, @NotNull Long newRevision,
                                         @NotNull Long newEpoch, @NotBlank String reason) { }

    /** Phản hồi tra cứu phân quyền — trả về vai trò, quyền hạn và revision/epoch hiện hành. */
    public record AuthorizationResponse(UUID treeId, UUID userId, String role,
                                        boolean canEdit, boolean canView,
                                        long revision, long epoch) { }
}