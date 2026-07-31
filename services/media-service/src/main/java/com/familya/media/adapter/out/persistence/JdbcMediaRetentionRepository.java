package com.familya.media.adapter.out.persistence;

import com.familya.media.application.port.out.MediaRetentionRepository;
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
 * Adapter đầu ra (outbound) — repository cho {@code media_retention_hold}.
 * <p>
 * Lưu giữ "retention hold" để đảm bảo media không bị xóa vĩnh viễn trước thời
 * điểm pháp lý cho phép (chờ khiếu nại, điều tra, v.v.). Mỗi lần place tạo
 * row mới; release chỉ set {@code released_at}.
 */
@Component
public class JdbcMediaRetentionRepository implements MediaRetentionRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Khởi tạo repository.
     *
     * @param jdbc JDBC template.
     */
    public JdbcMediaRetentionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Tạo một retention hold mới.
     *
     * @param mediaId   UUID media được giữ lại.
     * @param treeId    UUID cây.
     * @param holdUntil thời điểm hết hạn giữ.
     * @param reason    lý do giữ.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void placeHold(UUID mediaId, UUID treeId, Instant holdUntil, String reason) {
        // Tạo row mới với id ngẫu nhiên, released_at = NULL.
        jdbc.update(
                "INSERT INTO media_retention_hold (id, media_id, tree_id, hold_until, reason, released_at) "
                        + "VALUES (:id, :m, :t, :h, :r, NULL)",
                new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID().toString())
                        .addValue("m", mediaId.toString())
                        .addValue("t", treeId.toString())
                        .addValue("h", Timestamp.from(holdUntil))
                        .addValue("r", reason));
    }

    /**
     * Đánh dấu một hold đã được giải phóng (giữ row cho audit, không xóa).
     *
     * @param holdId     UUID hold.
     * @param releasedAt thời điểm giải phóng.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(UUID holdId, Instant releasedAt) {
        jdbc.update(
                "UPDATE media_retention_hold SET released_at = :r WHERE id = :id",
                new MapSqlParameterSource()
                        .addValue("r", Timestamp.from(releasedAt))
                        .addValue("id", holdId.toString()));
    }

    /**
     * Liệt kê các hold chưa release và đã đến hạn để worker cleanup xử lý.
     *
     * @param now   thời điểm hiện tại.
     * @param limit số lượng tối đa.
     * @return danh sách {@link HoldRow}.
     */
    @Override
    @Transactional(readOnly = true)
    public List<HoldRow> listReadyForCleanup(Instant now, int limit) {
        var rows = jdbc.queryForList(
                "SELECT id, media_id, tree_id, hold_until, reason FROM media_retention_hold "
                        + "WHERE released_at IS NULL AND hold_until <= :now ORDER BY hold_until ASC LIMIT :lim",
                new MapSqlParameterSource()
                        .addValue("now", Timestamp.from(now))
                        .addValue("lim", limit));
        return rows.stream().map(r -> new HoldRow(
                UUID.fromString((String) r.get("id")),
                UUID.fromString((String) r.get("media_id")),
                UUID.fromString((String) r.get("tree_id")),
                ((Timestamp) r.get("hold_until")).toInstant(),
                (String) r.get("reason"))).toList();
    }
}
