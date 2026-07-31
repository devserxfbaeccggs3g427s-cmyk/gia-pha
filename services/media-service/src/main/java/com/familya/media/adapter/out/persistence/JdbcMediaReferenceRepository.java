package com.familya.media.adapter.out.persistence;

import com.familya.media.application.port.out.MediaReferenceRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Adapter đầu ra (outbound) — repository cho {@code media_reference}, ghi nhận
 * quan hệ giữa media và target (member/event/album) cùng trạng thái.
 * <p>
 * Khóa duy nhất (media_id, target_kind, target_id) → dùng
 * {@code INSERT ... ON DUPLICATE KEY UPDATE} cho upsert.
 */
@Component
public class JdbcMediaReferenceRepository implements MediaReferenceRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Khởi tạo repository.
     *
     * @param jdbc JDBC template.
     */
    public JdbcMediaReferenceRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Upsert quan hệ media → target.
     * <p>
     * SQL dùng {@code ON DUPLICATE KEY UPDATE} dựa trên unique key
     * (media_id, target_kind, target_id).
     *
     * @param mediaId    UUID media.
     * @param treeId     UUID cây.
     * @param targetKind loại target (member/event/album...).
     * @param targetId   UUID target.
     * @param status     trạng thái mới.
     * @param at         thời điểm.
     * @param errorCode  mã lỗi gần nhất (null nếu thành công).
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void upsert(UUID mediaId, UUID treeId, String targetKind, UUID targetId,
                        String status, Instant at, String errorCode) {
        // ON DUPLICATE KEY dựa trên unique (media_id, target_kind, target_id).
        jdbc.update(
                "INSERT INTO media_reference (media_id, tree_id, target_kind, target_id, status, last_attempt_at, last_error_code) "
                        + "VALUES (:m, :t, :k, :i, :s, :at, :e) "
                        + "ON DUPLICATE KEY UPDATE status = VALUES(status), last_attempt_at = VALUES(last_attempt_at), last_error_code = VALUES(last_error_code)",
                new MapSqlParameterSource()
                        .addValue("m", mediaId.toString())
                        .addValue("t", treeId.toString())
                        .addValue("k", targetKind)
                        .addValue("i", targetId.toString())
                        .addValue("s", status)
                        .addValue("at", Timestamp.from(at))
                        .addValue("e", errorCode));
    }

    /**
     * Xóa một quan hệ media → target cụ thể.
     *
     * @param mediaId    UUID media.
     * @param targetKind loại target.
     * @param targetId   UUID target.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void clear(UUID mediaId, String targetKind, UUID targetId) {
        jdbc.update(
                "DELETE FROM media_reference WHERE media_id = :m AND target_kind = :k AND target_id = :i",
                new MapSqlParameterSource()
                        .addValue("m", mediaId.toString())
                        .addValue("k", targetKind)
                        .addValue("i", targetId.toString()));
    }

    /**
     * Liệt kê tất cả quan hệ của một media.
     *
     * @param mediaId UUID media.
     * @return danh sách {@link ReferenceRow}.
     */
    @Override
    @Transactional(readOnly = true)
    public List<ReferenceRow> listForMedia(UUID mediaId) {
        var rows = jdbc.queryForList(
                "SELECT media_id, tree_id, target_kind, target_id, status, last_attempt_at, last_error_code "
                        + "FROM media_reference WHERE media_id = :m",
                new MapSqlParameterSource("m", mediaId.toString()));
        return rows.stream().map(this::fromRow).toList();
    }

    /**
     * Liệt kê tất cả media gắn vào một target cụ thể trong một cây.
     *
     * @param treeId     UUID cây.
     * @param targetKind loại target.
     * @param targetId   UUID target.
     * @return danh sách {@link ReferenceRow}.
     */
    @Override
    @Transactional(readOnly = true)
    public List<ReferenceRow> listByTarget(UUID treeId, String targetKind, UUID targetId) {
        var rows = jdbc.queryForList(
                "SELECT media_id, tree_id, target_kind, target_id, status, last_attempt_at, last_error_code "
                        + "FROM media_reference WHERE tree_id = :t AND target_kind = :k AND target_id = :i",
                new MapSqlParameterSource()
                        .addValue("t", treeId.toString())
                        .addValue("k", targetKind)
                        .addValue("i", targetId.toString()));
        return rows.stream().map(this::fromRow).toList();
    }

    /** Helper chuyển row SQL sang {@link ReferenceRow}. */
    private ReferenceRow fromRow(java.util.Map<String, Object> r) {
        return new ReferenceRow(
                UUID.fromString((String) r.get("media_id")),
                UUID.fromString((String) r.get("tree_id")),
                (String) r.get("target_kind"),
                UUID.fromString((String) r.get("target_id")),
                (String) r.get("status"),
                r.get("last_attempt_at") == null ? null : ((Timestamp) r.get("last_attempt_at")).toInstant(),
                (String) r.get("last_error_code"));
    }
}
