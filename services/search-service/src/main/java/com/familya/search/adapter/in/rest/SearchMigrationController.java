package com.familya.search.adapter.in.rest;

import com.familya.search.application.port.out.SearchWatermarkRepository;
import com.familya.search.domain.model.Watermark;
import com.familya.platform.api.AsyncOperation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

/**
 * REST controller nội bộ phục vụ tái dựng (rebuild) và đối chiếu (reconcile)
 * projection của search service.
 *
 * <p>Các endpoint:</p>
 * <ul>
 *   <li>{@code POST /api/v2/internal/search/migration/projection/{domain}/rebuild}
 *       - upsert một bản ghi vào bảng chiếu của miền tương ứng.</li>
 *   <li>{@code GET /api/v2/internal/search/migration/reconcile}
 *       - trả về barrier watermark hiện tại của cây.</li>
 * </ul>
 *
 * <p>Các endpoint này chỉ phục vụ mục đích vận hành/migration, không nên
 * được gọi từ luồng người dùng cuối.</p>
 */
@RestController
@RequestMapping(path = "/api/v2/internal/search", produces = MediaType.APPLICATION_JSON_VALUE)
public class SearchMigrationController {

    private final SearchWatermarkRepository watermark;
    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Khởi tạo controller với cổng watermark và JDBC template.
     */
    public SearchMigrationController(SearchWatermarkRepository watermark, NamedParameterJdbcTemplate jdbc) {
        this.watermark = watermark;
        this.jdbc = jdbc;
    }

    /**
     * Rebuild một bản ghi projection cho miền {@code domain}. Sau khi upsert
     * thành công, watermark của miền được nâng lên {@code req.watermark()}.
     *
     * @param domain        miền dữ liệu (MEMBER, EVENT, MEDIA, TREE, RELATIONSHIP).
     * @param correlationId header đối chiếu - hiện không sử dụng trong thân hàm
     *                      nhưng vẫn được khai báo để API có logging/tracing.
     * @param req           payload yêu cầu rebuild (đã validate).
     * @return {@link AsyncOperation} mô tả thao tác đã chấp nhận.
     */
    @PostMapping(path = "/migration/projection/{domain}/rebuild", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<AsyncOperation> rebuild(@PathVariable String domain,
                                                    @RequestHeader("X-Correlation-Id") String correlationId,
                                                    @Valid @RequestBody RebuildRequest req) {
        Watermark.Domain d = Watermark.Domain.valueOf(domain.toUpperCase());
        rebuildForDomain(d, req);
        watermark.advance(req.treeId(), d, req.watermark());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(AsyncOperation.accepted(req.treeId(), "/api/v2/operations/" + req.treeId()));
    }

    /**
     * Điều phối upsert vào bảng chiếu tương ứng với miền. Các miền không có
     * bảng chiếu riêng (TREE, RELATIONSHIP) là không-op.
     *
     * @param domain miền cần rebuild.
     * @param req    payload yêu cầu rebuild.
     */
    private void rebuildForDomain(Watermark.Domain domain, RebuildRequest req) {
        switch (domain) {
            case MEMBER -> upsertMemberDoc(req);
            case EVENT -> upsertEventDoc(req);
            case MEDIA -> upsertMediaDoc(req);
            case TREE -> { /* no-op: tree-level watermarks are advanced only */ }
            case RELATIONSHIP -> { /* not directly exposed; rebuild via member-side */ }
        }
    }

    /**
     * Upsert vào bảng {@code search_member_doc} dựa trên payload trong {@code req}.
     *
     * @param req yêu cầu rebuild.
     */
    private void upsertMemberDoc(RebuildRequest req) {
        jdbc.update(
                "INSERT INTO search_member_doc (tree_id, member_id, full_name, given_name, surname, birth_year, death_year, tombstoned, normalized_name, last_updated) "
                        + "VALUES (:t, :m, :name, :g, :s, :by, :dy, :tomb, :norm, :u) "
                        + "ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), given_name = VALUES(given_name), surname = VALUES(surname), "
                        + "birth_year = VALUES(birth_year), death_year = VALUES(death_year), tombstoned = VALUES(tombstoned), "
                        + "normalized_name = VALUES(normalized_name), last_updated = VALUES(last_updated)",
                memberParams(req));
    }

    /**
     * Tạo {@link MapSqlParameterSource} cho bảng member từ payload yêu cầu.
     *
     * @param req yêu cầu rebuild.
     * @return tham số cho câu lệnh upsert.
     */
    private MapSqlParameterSource memberParams(RebuildRequest req) {
        return baseParams(req)
                .addValue("name", req.payload() == null ? "" : String.valueOf(req.payload().getOrDefault("fullName", "")))
                .addValue("g", req.payload() == null ? null : req.payload().get("givenName"))
                .addValue("s", req.payload() == null ? null : req.payload().get("surname"))
                .addValue("by", req.payload() == null ? null : req.payload().get("birthYear"))
                .addValue("dy", req.payload() == null ? null : req.payload().get("deathYear"))
                .addValue("tomb", Boolean.TRUE.equals(req.payload() == null ? null : req.payload().get("tombstoned")))
                .addValue("norm", req.normalizedName());
    }

    /**
     * Upsert vào bảng {@code search_event_doc}.
     *
     * @param req yêu cầu rebuild.
     */
    private void upsertEventDoc(RebuildRequest req) {
        jdbc.update(
                "INSERT INTO search_event_doc (tree_id, event_id, title, start_date, kind, tombstoned, normalized_title, last_updated) "
                        + "VALUES (:t, :e, :title, :d, :k, :tomb, :norm, :u) "
                        + "ON DUPLICATE KEY UPDATE title = VALUES(title), start_date = VALUES(start_date), kind = VALUES(kind), "
                        + "tombstoned = VALUES(tombstoned), normalized_title = VALUES(normalized_title), last_updated = VALUES(last_updated)",
                baseParams(req)
                        .addValue("title", req.payload() == null ? "" : String.valueOf(req.payload().getOrDefault("title", "")))
                        .addValue("d", req.payload() == null ? null : req.payload().get("startDate"))
                        .addValue("k", req.payload() == null ? null : req.payload().get("kind"))
                        .addValue("tomb", Boolean.TRUE.equals(req.payload() == null ? null : req.payload().get("tombstoned")))
                        .addValue("norm", req.normalizedName()));
    }

    /**
     * Upsert vào bảng {@code search_media_doc}. Nếu không có {@code kind} thì
     * mặc định {@code "OTHER"}.
     *
     * @param req yêu cầu rebuild.
     */
    private void upsertMediaDoc(RebuildRequest req) {
        jdbc.update(
                "INSERT INTO search_media_doc (tree_id, media_id, filename, kind, tombstoned, normalized_filename, last_updated) "
                        + "VALUES (:t, :m, :name, :k, :tomb, :norm, :u) "
                        + "ON DUPLICATE KEY UPDATE filename = VALUES(filename), kind = VALUES(kind), tombstoned = VALUES(tombstoned), "
                        + "normalized_filename = VALUES(normalized_filename), last_updated = VALUES(last_updated)",
                baseParams(req)
                        .addValue("name", req.payload() == null ? "" : String.valueOf(req.payload().getOrDefault("filename", "")))
                        .addValue("k", req.payload() == null ? "OTHER" : String.valueOf(req.payload().getOrDefault("kind", "OTHER")))
                        .addValue("tomb", Boolean.TRUE.equals(req.payload() == null ? null : req.payload().get("tombstoned")))
                        .addValue("norm", req.normalizedName()));
    }

    /**
     * Tạo các tham số cơ sở (treeId, entityId dùng chung cho cả member/event,
     * media và last_updated) cho các câu upsert.
     *
     * @param req yêu cầu rebuild.
     * @return {@link MapSqlParameterSource} chứa các tham số cơ sở.
     */
    private MapSqlParameterSource baseParams(RebuildRequest req) {
        return new MapSqlParameterSource()
                .addValue("t", req.treeId().toString())
                .addValue("m", req.entityId() == null ? null : req.entityId().toString())
                .addValue("e", req.entityId() == null ? null : req.entityId().toString())
                .addValue("u", java.sql.Timestamp.from(Instant.now()));
    }

    /**
     * Trả về barrier watermark hiện tại của cây, dùng để đối chiếu.
     *
     * @param treeId định danh cây.
     * @return {@link ReconcileResponse} chứa treeId và bản đồ watermark.
     */
    @GetMapping("/migration/reconcile")
    public ResponseEntity<ReconcileResponse> reconcile(@RequestParam UUID treeId) {
        var barrier = watermark.barrierFor(treeId);
        return ResponseEntity.ok(new ReconcileResponse(treeId, barrier.values()));
    }

    /**
     * Payload yêu cầu rebuild một bản ghi projection.
     *
     * @param treeId        định danh cây (bắt buộc).
     * @param entityId      định danh thực thể (member/event/media) - có thể null.
     * @param normalizedName tên/tiêu đề đã chuẩn hoá (bắt buộc).
     * @param watermark     watermark tương ứng.
     * @param payload       các thuộc tính bổ sung tuỳ miền.
     */
    public record RebuildRequest(
            @NotNull UUID treeId,
            UUID entityId,
            @NotNull String normalizedName,
            long watermark,
            java.util.Map<String, Object> payload) { }

    /**
     * Phản hồi đối chiếu - bản đồ watermark theo miền của cây.
     *
     * @param treeId    định danh cây.
     * @param watermarks bản đồ từ miền sang watermark hiện tại.
     */
    public record ReconcileResponse(UUID treeId, java.util.Map<Watermark.Domain, Long> watermarks) { }
}
