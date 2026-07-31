package com.familya.sharing.adapter.out.projection;

import com.familya.sharing.application.port.out.AllowlistedProjectionRepository;
import com.familya.sharing.application.port.out.AllowlistedProjectionRepository.ShareScope;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Adapter JDBC hiện thực {@link AllowlistedProjectionRepository} &mdash; thao
 * tác với các bảng projection allowlist dùng cho sharing-service.
 * <p>
 * Có năm bảng projection tương ứng với các domain:
 * <ul>
 *     <li>{@code share_member_projection}</li>
 *     <li>{@code share_media_projection}</li>
 *     <li>{@code share_event_projection}</li>
 *     <li>{@code share_relationship_projection}</li>
 *     <li>{@code share_tree_projection}</li>
 * </ul>
 * và bảng {@code share_watermark} lưu watermark theo từng domain.
 * <p>
 * Dữ liệu allowlist được serialize sang JSON bằng Jackson. Các phương thức ghi
 * yêu cầu caller đã mở transaction ({@link Propagation#MANDATORY}).
 */
@Component
public class JdbcAllowlistedProjectionRepository implements AllowlistedProjectionRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Khởi tạo adapter.
     *
     * @param jdbc template JDBC chia sẻ.
     */
    public JdbcAllowlistedProjectionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Đọc watermark hiện tại của một (cây, domain).
     *
     * @param treeId định danh cây gia phả.
     * @param domain miền dữ liệu.
     * @return giá trị watermark hoặc {@code 0} nếu chưa có bản ghi.
     */
    @Override
    @Transactional(readOnly = true)
    public long readWatermark(UUID treeId, String domain) {
        var rows = jdbc.queryForList(
                "SELECT watermark FROM share_watermark WHERE tree_id = :t AND domain = :d",
                new MapSqlParameterSource().addValue("t", treeId.toString()).addValue("d", domain));
        if (rows.isEmpty()) return 0L;
        return ((Number) rows.get(0).get("watermark")).longValue();
    }

    /**
     * Cập nhật watermark cho một (cây, domain). Sử dụng {@code GREATEST} để
     * đảm bảo watermark không bao giờ "lùi" khi nhận được sự kiện cũ.
     *
     * @param treeId    định danh cây gia phả.
     * @param domain    miền dữ liệu.
     * @param watermark giá trị watermark mới.
     * @param at        thời điểm cập nhật.
     * @return giá trị watermark đã được ghi nhận.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public long advanceWatermark(UUID treeId, String domain, long watermark, Instant at) {
        jdbc.update(
                "INSERT INTO share_watermark (tree_id, domain, watermark, last_updated) "
                        + "VALUES (:t, :d, :w, :u) "
                        + "ON DUPLICATE KEY UPDATE watermark = GREATEST(watermark, VALUES(watermark)), last_updated = VALUES(last_updated)",
                new MapSqlParameterSource()
                        .addValue("t", treeId.toString())
                        .addValue("d", domain)
                        .addValue("w", watermark)
                        .addValue("u", Timestamp.from(at)));
        return watermark;
    }

    /**
     * Lưu projection cho một thành viên.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveMember(UUID treeId, UUID memberId, Map<String, Object> allowlisted, boolean tombstoned, Instant at) {
        upsert("share_member_projection", treeId, memberId, allowlisted, tombstoned, at);
    }

    /**
     * Lưu projection cho một media.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveMedia(UUID treeId, UUID mediaId, Map<String, Object> allowlisted, boolean tombstoned, Instant at) {
        upsert("share_media_projection", treeId, mediaId, allowlisted, tombstoned, at);
    }

    /**
     * Lưu projection cho một sự kiện.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveEvent(UUID treeId, UUID eventId, Map<String, Object> allowlisted, boolean tombstoned, Instant at) {
        upsert("share_event_projection", treeId, eventId, allowlisted, tombstoned, at);
    }

    /**
     * Lưu projection cho một quan hệ.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveRelationship(UUID treeId, UUID relId, Map<String, Object> allowlisted, boolean tombstoned, Instant at) {
        upsert("share_relationship_projection", treeId, relId, allowlisted, tombstoned, at);
    }

    /**
     * Lưu projection cho cả cây &mdash; dùng bảng riêng với khóa chính chỉ
     * gồm {@code tree_id}.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveTree(UUID treeId, Map<String, Object> allowlisted, boolean tombstoned, Instant at) {
        jdbc.update(
                "INSERT INTO share_tree_projection (tree_id, allowlisted, tombstoned, last_updated) "
                        + "VALUES (:t, :j, :tomb, :u) "
                        + "ON DUPLICATE KEY UPDATE allowlisted = VALUES(allowlisted), tombstoned = VALUES(tombstoned), last_updated = VALUES(last_updated)",
                params(treeId, null, allowlisted, tombstoned, at));
    }

    /**
     * Lưu projection công khai cho một (cây, scope, targetId) kèm watermark.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void savePublicProjection(UUID treeId, ShareScope scope, UUID targetId, Map<String, Object> value,
                                       long watermark, Instant at) {
        try {
            jdbc.update(
                    "INSERT INTO share_allowlisted_projection (tree_id, scope, target_id, allowlisted_json, watermark, last_updated) "
                            + "VALUES (:t, :s, :id, :j, :w, :u) "
                            + "ON DUPLICATE KEY UPDATE allowlisted_json = VALUES(allowlisted_json), watermark = VALUES(watermark), last_updated = VALUES(last_updated)",
                    new MapSqlParameterSource()
                            .addValue("t", treeId.toString())
                            .addValue("s", scope.name())
                            .addValue("id", targetId == null ? "" : targetId.toString())
                            .addValue("j", mapper.writeValueAsString(value))
                            .addValue("w", watermark)
                            .addValue("u", Timestamp.from(at)));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot serialize allowlisted projection", e);
        }
    }

    /**
     * Đọc projection công khai đã lưu. Trả về {@link Map#of()} khi chưa có.
     *
     * @throws IllegalStateException nếu JSON không hợp lệ.
     */
    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> readPublicProjection(UUID treeId, ShareScope scope, UUID targetId) {
        var rows = jdbc.queryForList(
                "SELECT allowlisted_json FROM share_allowlisted_projection "
                        + "WHERE tree_id = :t AND scope = :s AND target_id <=> :id",
                new MapSqlParameterSource()
                        .addValue("t", treeId.toString())
                        .addValue("s", scope.name())
                        .addValue("id", targetId == null ? null : targetId.toString()));
        if (rows.isEmpty()) return Map.of();
        try {
            return mapper.readValue((String) rows.get(0).get("allowlisted_json"), new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Cannot deserialize allowlisted projection", e);
        }
    }

    /**
     * Thực hiện upsert cho một trong các bảng projection theo domain.
     * <p>
     * Tên cột khoá chính phụ thuộc vào bảng &mdash; được xác định bởi
     * {@link #idColumn(String)}.
     *
     * @param table       tên bảng projection.
     * @param treeId      định danh cây.
     * @param entityId    định danh thực thể (member/media/event/relationship).
     * @param allowlisted {@code Map} các trường allowlist.
     * @param tombstoned  {@code true} nếu đã tombstone.
     * @param at          thời điểm cập nhật.
     */
    private void upsert(String table, UUID treeId, UUID entityId, Map<String, Object> allowlisted,
                          boolean tombstoned, Instant at) {
        try {
            jdbc.update(
                    "INSERT INTO " + table + " (tree_id, " + idColumn(table) + ", allowlisted, tombstoned, last_updated) "
                            + "VALUES (:t, :id, :j, :tomb, :u) "
                            + "ON DUPLICATE KEY UPDATE allowlisted = VALUES(allowlisted), tombstoned = VALUES(tombstoned), last_updated = VALUES(last_updated)",
                    params(treeId, entityId, allowlisted, tombstoned, at));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot write projection", e);
        }
    }

    /**
     * Đóng gói tham số dùng chung cho cả {@code upsert} và {@code saveTree}.
     */
    private MapSqlParameterSource params(UUID treeId, UUID entityId, Map<String, Object> allowlisted,
                                           boolean tombstoned, Instant at) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("t", treeId.toString())
                .addValue("id", entityId == null ? null : entityId.toString())
                .addValue("tomb", tombstoned)
                .addValue("u", Timestamp.from(at));
        try {
            // Cho phép null &mdash; thay bằng map rỗng để JSON vẫn hợp lệ.
            p.addValue("j", mapper.writeValueAsString(allowlisted == null ? new HashMap<>() : allowlisted));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot serialize projection", e);
        }
        return p;
    }

    /**
     * Ánh xạ tên bảng sang tên cột khoá chính phụ.
     *
     * @param table tên bảng projection.
     * @return tên cột khoá chính phụ tương ứng, mặc định {@code "id"}.
     */
    private static String idColumn(String table) {
        return switch (table) {
            case "share_member_projection" -> "member_id";
            case "share_media_projection" -> "media_id";
            case "share_event_projection" -> "event_id";
            case "share_relationship_projection" -> "relationship_id";
            default -> "id";
        };
    }
}