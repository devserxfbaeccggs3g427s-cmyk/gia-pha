package com.familya.relationship.adapter.in.rest;

import com.familya.platform.api.AsyncOperation;
import com.familya.relationship.application.port.in.CreateRelationshipCommand;
import com.familya.relationship.application.port.in.TombstoneRelationshipCommand;
import com.familya.relationship.application.usecase.CreateRelationshipUseCase;
import com.familya.relationship.application.usecase.QueryGraphUseCase;
import com.familya.relationship.application.usecase.TombstoneRelationshipUseCase;
import com.familya.relationship.domain.model.Relationship;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * REST controller cho các thao tác trên quan hệ gia phả.
 * <p>
 * Tất cả endpoint đều nằm dưới prefix {@code /api/v2/trees/{treeId}/relationships}.
 * </p>
 *
 * <h2>Các endpoint</h2>
 * <ul>
 *   <li>{@code POST /} - tạo quan hệ mới.</li>
 *   <li>{@code DELETE /{relationshipId}} - đánh dấu xóa mềm.</li>
 *   <li>{@code GET /generations?root=...} - tính thế hệ từ một thành viên gốc.</li>
 *   <li>{@code GET /ancestors/{memberId}} - trả về tổ tiên của một thành viên.</li>
 *   <li>{@code GET /spouses/{memberId}} - trả về vợ/chồng của một thành viên.</li>
 *   <li>{@code GET /adoptions/{memberId}} - trả về quan hệ nhận nuôi liên quan.</li>
 * </ul>
 *
 * <h2>Xác thực và phân quyền</h2>
 * <p>
 * Phân quyền được thực hiện ở tầng use case dựa trên projection {@code authorization_projection}.
 * Controller chỉ lấy {@code X-Acting-User} từ header (do gateway đặt sau khi đã
 * xác thực JWT).
 * </p>
 */
@RestController
@RequestMapping(path = "/api/v2/trees/{treeId}/relationships", produces = MediaType.APPLICATION_JSON_VALUE)
public class RelationshipController {

    /** Use case tạo quan hệ. */
    private final CreateRelationshipUseCase createRelationship;
    /** Use case tombstone. */
    private final TombstoneRelationshipUseCase tombstoneRelationship;
    /** Use case truy vấn đồ thị (read-only). */
    private final QueryGraphUseCase queryGraph;

    /**
     * Khởi tạo controller.
     *
     * @param createRelationship  use case tạo quan hệ
     * @param tombstoneRelationship use case tombstone
     * @param queryGraph          use case truy vấn
     */
    public RelationshipController(CreateRelationshipUseCase createRelationship,
                                  TombstoneRelationshipUseCase tombstoneRelationship,
                                  QueryGraphUseCase queryGraph) {
        this.createRelationship = createRelationship;
        this.tombstoneRelationship = tombstoneRelationship;
        this.queryGraph = queryGraph;
    }

    /**
     * Tạo một quan hệ mới.
     * <p>
     * Header yêu cầu: {@code X-Acting-User} (UUID người dùng). Tùy chọn:
     * {@code X-Tree-Revision} (phiên bản projection phân quyền mà client kỳ vọng).
     * </p>
     *
     * @param actingUser          định danh người dùng (từ gateway)
     * @param treeId              định danh cây (từ URL)
     * @param expectedTreeRevision phiên bản projection phân quyền (tùy chọn)
     * @param req                 body yêu cầu (đã validate)
     * @return {@code 202 ACCEPTED} cùng {@link AsyncOperation} mô tả thao tác
     */
    @PostMapping
    public ResponseEntity<AsyncOperation> create(@RequestHeader("X-Acting-User") UUID actingUser,
                                                  @PathVariable UUID treeId,
                                                  @RequestHeader(value = "X-Tree-Revision", required = false) Long expectedTreeRevision,
                                                  @Valid @RequestBody CreateRelationshipRequest req) {
        // Mặc định expectedTreeRevision = 0 nếu header thiếu (tương thích ngược).
        long er = expectedTreeRevision == null ? 0L : expectedTreeRevision;
        UUID id = createRelationship.execute(new CreateRelationshipCommand(
                treeId, actingUser, Relationship.Kind.valueOf(req.kind()),
                req.fromMemberId(), req.toMemberId(), req.metadataJson(), er));
        // Trả về 202 ACCEPTED kèm Location header cho client theo dõi trạng thái.
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .header("Location", "/api/v2/trees/" + treeId + "/relationships/" + id)
                .body(AsyncOperation.accepted(id, "/api/v2/operations/" + id));
    }

    /**
     * Đánh dấu xóa mềm một quan hệ.
     * <p>
     * Header tùy chọn: {@code If-Match} (phiên bản aggregate mong đợi) và
     * {@code X-Tree-Revision} (phiên bản projection phân quyền).
     * </p>
     *
     * @param actingUser          định danh người dùng
     * @param treeId              định danh cây
     * @param relationshipId      định danh quan hệ cần xóa
     * @param expectedVersion     phiên bản aggregate (tùy chọn, mặc định 0)
     * @param expectedTreeRevision phiên bản projection (tùy chọn, mặc định 0)
     * @return {@code 202 ACCEPTED}
     */
    @DeleteMapping("/{relationshipId}")
    public ResponseEntity<AsyncOperation> tombstone(@RequestHeader("X-Acting-User") UUID actingUser,
                                                     @PathVariable UUID treeId,
                                                     @PathVariable UUID relationshipId,
                                                     @RequestHeader(value = "If-Match", required = false) Long expectedVersion,
                                                     @RequestHeader(value = "X-Tree-Revision", required = false) Long expectedTreeRevision) {
        // Mặc định an toàn khi client không gửi header.
        long ev = expectedVersion == null ? 0L : expectedVersion;
        long er = expectedTreeRevision == null ? 0L : expectedTreeRevision;
        tombstoneRelationship.execute(new TombstoneRelationshipCommand(relationshipId, actingUser, ev, er));
        return ResponseEntity.accepted().body(AsyncOperation.accepted(relationshipId,
                "/api/v2/operations/" + relationshipId));
    }

    /**
     * Tính số thế hệ cho mọi thành viên trong cây, bắt đầu từ {@code root}.
     *
     * @param treeId định danh cây
     * @param root   thành viên gốc
     * @return {@code Map} từ định danh thành viên sang số thế hệ
     */
    @GetMapping("/generations")
    public ResponseEntity<Map<UUID, Integer>> generations(@PathVariable UUID treeId,
                                                           @RequestParam("root") UUID root) {
        return ResponseEntity.ok(queryGraph.generations(treeId, root));
    }

    /**
     * Trả về tập tổ tiên của một thành viên.
     *
     * @param treeId   định danh cây
     * @param memberId thành viên cần truy vấn
     * @return tập định danh các tổ tiên
     */
    @GetMapping("/ancestors/{memberId}")
    public ResponseEntity<Set<UUID>> ancestors(@PathVariable UUID treeId, @PathVariable UUID memberId) {
        return ResponseEntity.ok(queryGraph.ancestors(treeId, memberId));
    }

    /**
     * Trả về tập vợ/chồng của một thành viên.
     *
     * @param treeId   định danh cây
     * @param memberId thành viên cần truy vấn
     * @return tập định danh vợ/chồng
     */
    @GetMapping("/spouses/{memberId}")
    public ResponseEntity<Set<UUID>> spouses(@PathVariable UUID treeId, @PathVariable UUID memberId) {
        return ResponseEntity.ok(queryGraph.spouses(treeId, memberId));
    }

    /**
     * Trả về tập quan hệ nhận nuôi liên quan tới một thành viên.
     *
     * @param treeId   định danh cây
     * @param memberId thành viên cần truy vấn
     * @return tập định danh liên quan
     */
    @GetMapping("/adoptions/{memberId}")
    public ResponseEntity<Set<UUID>> adoptions(@PathVariable UUID treeId, @PathVariable UUID memberId) {
        return ResponseEntity.ok(queryGraph.adoptions(treeId, memberId));
    }

    /**
     * DTO cho yêu cầu tạo quan hệ.
     *
     * @param kind         loại quan hệ (PARENT_CHILD / SPOUSE / ADOPTION)
     * @param fromMemberId thành viên phía nguồn
     * @param toMemberId   thành viên phía đích
     * @param metadataJson chuỗi JSON metadata (tùy chọn)
     */
    public record CreateRelationshipRequest(@NotNull String kind,
                                              @NotNull UUID fromMemberId,
                                              @NotNull UUID toMemberId,
                                              String metadataJson) { }
}