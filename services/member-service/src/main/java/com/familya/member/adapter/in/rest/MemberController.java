package com.familya.member.adapter.in.rest;

import com.familya.member.application.port.in.CreateMemberCommand;
import com.familya.member.application.port.in.InitiateDeleteMemberCommand;
import com.familya.member.application.port.in.MergeMembersCommand;
import com.familya.member.application.port.in.UpdateMemberCommand;
import com.familya.member.application.usecase.CreateMemberUseCase;
import com.familya.member.application.usecase.DeleteMemberSagaService;
import com.familya.member.application.usecase.MergeMembersUseCase;
import com.familya.member.application.usecase.UpdateMemberUseCase;
import com.familya.member.domain.model.Member;
import com.familya.platform.api.AsyncOperation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * REST controller cho các thao tác CRUD trên thành viên trong một cây gia phả. Đây là bean
 * {@code @RestController} thuộc tầng adapter-in, cung cấp HTTP API chuẩn cho client.
 *
 * <p>Tất cả các thao tác đều trả về {@link AsyncOperation} vì nghiệp vụ xử lý bất đồng bộ
 * (đặc biệt là xóa thành viên thông qua Saga phân tán).
 */
@RestController
@RequestMapping(path = "/api/v2/trees/{treeId}/members", produces = MediaType.APPLICATION_JSON_VALUE)
public class MemberController {

    private final CreateMemberUseCase createMember;
    private final UpdateMemberUseCase updateMember;
    private final DeleteMemberSagaService deleteMemberSaga;
    private final MergeMembersUseCase mergeMembers;

    /**
     * Khởi tạo controller với các use case tương ứng.
     *
     * @param createMember    use case tạo thành viên
     * @param updateMember    use case cập nhật thành viên
     * @param deleteMemberSaga dịch vụ Saga xóa thành viên
     * @param mergeMembers    use case gộp hai thành viên
     */
    public MemberController(CreateMemberUseCase createMember, UpdateMemberUseCase updateMember,
                            DeleteMemberSagaService deleteMemberSaga, MergeMembersUseCase mergeMembers) {
        this.createMember = createMember;
        this.updateMember = updateMember;
        this.deleteMemberSaga = deleteMemberSaga;
        this.mergeMembers = mergeMembers;
    }

    /**
     * Tạo mới một thành viên trong cây.
     *
     * @param actingUser            người dùng thực hiện hành động (lấy từ header {@code X-Acting-User})
     * @param treeId                mã cây (từ đường dẫn)
     * @param expectedTreeRevision  phiên bản cây kỳ vọng (header {@code If-Match}, dùng cho optimistic concurrency)
     * @param req                   payload yêu cầu tạo thành viên
     * @return phản hồi HTTP 202 Accepted kèm {@link AsyncOperation} và header Location trỏ tới resource mới
     */
    @PostMapping
    public ResponseEntity<AsyncOperation> create(@RequestHeader("X-Acting-User") UUID actingUser,
                                                 @PathVariable UUID treeId,
                                                 @RequestHeader(value = "If-Match", required = false) Long expectedTreeRevision,
                                                 @Valid @RequestBody CreateMemberRequest req) {
        long er = expectedTreeRevision == null ? 0L : expectedTreeRevision;
        UUID id = createMember.execute(new CreateMemberCommand(
                treeId, actingUser, req.userId(),
                req.displayName(), req.givenName(), req.surname(),
                req.birthDate(), req.deathDate(),
                Boolean.TRUE.equals(req.birthYearKnown()), Boolean.TRUE.equals(req.deathYearKnown()),
                req.gender() == null ? null : Member.Gender.valueOf(req.gender()),
                req.status() == null ? Member.Status.LIVING : Member.Status.valueOf(req.status()),
                req.generation(), req.legacyAvatarUrl(), req.notes(), er));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .header("Location", "/api/v2/trees/" + treeId + "/members/" + id)
                .body(AsyncOperation.accepted(id, "/api/v2/operations/" + id));
    }

    /**
     * Cập nhật thông tin một thành viên đã tồn tại.
     *
     * @param actingUser            người dùng thực hiện hành động
     * @param treeId                mã cây
     * @param memberId              mã thành viên cần cập nhật
     * @param expectedVersion       phiên bản kỳ vọng của thành viên (header {@code If-Match})
     * @param expectedTreeRevision  phiên bản kỳ vọng của cây (header {@code X-Tree-Revision})
     * @param req                   payload cập nhật
     * @return phản hồi HTTP 202 Accepted
     */
    @PutMapping("/{memberId}")
    public ResponseEntity<AsyncOperation> update(@RequestHeader("X-Acting-User") UUID actingUser,
                                                  @PathVariable UUID treeId,
                                                  @PathVariable UUID memberId,
                                                  @RequestHeader(value = "If-Match", required = false) Long expectedVersion,
                                                  @RequestHeader(value = "X-Tree-Revision", required = false) Long expectedTreeRevision,
                                                  @Valid @RequestBody UpdateMemberRequest req) {
        long ev = expectedVersion == null ? 0L : expectedVersion;
        long er = expectedTreeRevision == null ? 0L : expectedTreeRevision;
        updateMember.execute(new UpdateMemberCommand(memberId, actingUser, ev, er,
                req.displayName(), req.givenName(), req.surname(),
                req.birthDate(), req.deathDate(),
                req.gender() == null ? null : Member.Gender.valueOf(req.gender()),
                req.generation(), req.notes()));
        return ResponseEntity.accepted().body(AsyncOperation.accepted(memberId, "/api/v2/operations/" + memberId));
    }

    /**
     * Khởi tạo Saga xóa thành viên. Phản hồi HTTP mang {@code operationId} bền vững
     * (KHÔNG phải memberId). Client sẽ thăm dò {@code /api/v2/operations/{operationId}}
     * để theo dõi trạng thái Saga.
     *
     * @param actingUser            người dùng thực hiện
     * @param treeId                mã cây
     * @param memberId              mã thành viên cần xóa
     * @param expectedVersion       phiên bản kỳ vọng của thành viên
     * @param expectedTreeRevision  phiên bản kỳ vọng của cây
     * @param expectedTreeEpoch     epoch kỳ vọng của cây
     * @return phản hồi HTTP 202 Accepted kèm operationId
     */
    @DeleteMapping("/{memberId}")
    public ResponseEntity<AsyncOperation> tombstone(@RequestHeader("X-Acting-User") UUID actingUser,
                                                     @PathVariable UUID treeId,
                                                     @PathVariable UUID memberId,
                                                     @RequestHeader(value = "If-Match", required = false) Long expectedVersion,
                                                     @RequestHeader(value = "X-Tree-Revision", required = false) Long expectedTreeRevision,
                                                     @RequestHeader(value = "X-Tree-Epoch", required = false) Long expectedTreeEpoch) {
        long ev = expectedVersion == null ? 0L : expectedVersion;
        long er = expectedTreeRevision == null ? 0L : expectedTreeRevision;
        long ee = expectedTreeEpoch == null ? 0L : expectedTreeEpoch;
        UUID operationId = deleteMemberSaga.initiate(new InitiateDeleteMemberCommand(
                treeId, memberId, actingUser, ev, er, ee));
        return ResponseEntity.accepted()
                .header("Location", "/api/v2/operations/" + operationId)
                .body(AsyncOperation.accepted(operationId, "/api/v2/operations/" + operationId));
    }

    /**
     * Gộp hai thành viên trong cùng một cây thành một. Member nguồn sẽ bị tombstone.
     *
     * @param actingUser           người dùng thực hiện
     * @param treeId               mã cây
     * @param memberId             mã thành viên survivor (giữ lại)
     * @param expectedVersion      phiên bản kỳ vọng của survivor
     * @param expectedTreeRevision phiên bản kỳ vọng của cây
     * @param req                  payload chứa mã thành viên nguồn cần gộp vào
     * @return phản hồi HTTP 202 Accepted
     */
    @PostMapping("/{memberId}/merge")
    public ResponseEntity<AsyncOperation> merge(@RequestHeader("X-Acting-User") UUID actingUser,
                                                 @PathVariable UUID treeId,
                                                 @PathVariable UUID memberId,
                                                 @RequestHeader(value = "If-Match", required = false) Long expectedVersion,
                                                 @RequestHeader(value = "X-Tree-Revision", required = false) Long expectedTreeRevision,
                                                 @RequestBody MergeRequest req) {
        long ev = expectedVersion == null ? 0L : expectedVersion;
        long er = expectedTreeRevision == null ? 0L : expectedTreeRevision;
        mergeMembers.execute(new MergeMembersCommand(memberId, req.sourceMemberId(), actingUser, ev, er));
        return ResponseEntity.accepted().body(AsyncOperation.accepted(memberId, "/api/v2/operations/" + memberId));
    }

    /**
     * Payload yêu cầu tạo thành viên. Áp dụng Bean Validation cho các trường bắt buộc.
     *
     * @param displayName      tên hiển thị (bắt buộc, không rỗng)
     * @param userId           mã người dùng hệ thống (tùy chọn)
     * @param givenName        tên
     * @param surname          họ
     * @param birthDate        ngày sinh
     * @param deathDate        ngày mất
     * @param birthYearKnown   cờ đánh dấu năm sinh đã biết chính xác
     * @param deathYearKnown   cờ đánh dấu năm mất đã biết chính xác
     * @param gender           giới tính (chuỗi tên enum)
     * @param status           trạng thái (chuỗi tên enum, mặc định LIVING)
     * @param generation       thế hệ trong cây
     * @param legacyAvatarUrl  URL ảnh đại diện cũ (tương thích ngược)
     * @param notes            ghi chú tự do
     */
    public record CreateMemberRequest(
            @NotBlank String displayName,
            UUID userId,
            String givenName,
            String surname,
            LocalDate birthDate,
            LocalDate deathDate,
            Boolean birthYearKnown,
            Boolean deathYearKnown,
            String gender,
            String status,
            Integer generation,
            String legacyAvatarUrl,
            String notes) { }

    /**
     * Payload yêu cầu cập nhật thành viên. Tất cả trường đều tùy chọn vì cập nhật có thể chỉ chạm một phần.
     *
     * @param displayName  tên hiển thị mới
     * @param givenName    tên mới
     * @param surname      họ mới
     * @param birthDate    ngày sinh mới
     * @param deathDate    ngày mất mới
     * @param gender       giới tính mới
     * @param generation   thế hệ mới
     * @param notes        ghi chú mới
     */
    public record UpdateMemberRequest(
            String displayName,
            String givenName,
            String surname,
            LocalDate birthDate,
            LocalDate deathDate,
            String gender,
            Integer generation,
            String notes) { }

    /**
     * Payload yêu cầu gộp hai thành viên.
     *
     * @param sourceMemberId mã thành viên nguồn sẽ bị tombstone sau khi gộp
     */
    public record MergeRequest(@NotNull UUID sourceMemberId) { }
}