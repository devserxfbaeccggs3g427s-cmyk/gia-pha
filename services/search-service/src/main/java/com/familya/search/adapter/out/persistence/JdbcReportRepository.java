package com.familya.search.adapter.out.persistence;

import com.familya.search.application.port.out.ReportRepository;
import com.familya.search.domain.model.ReportSnapshot;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

/**
 * Triển khai JDBC của {@link ReportRepository}, đọc/ghi bảng {@code report_snapshot}.
 */
@Component
public class JdbcReportRepository implements ReportRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Khởi tạo repository với JDBC template dùng chung.
     *
     * @param jdbc JDBC template dùng để truy vấn/lưu/xoá.
     */
    public JdbcReportRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Xoá toàn bộ bản chụp báo cáo của một cây.
     *
     * @param treeId định danh cây gia phả.
     * @return số bản ghi đã xoá.
     */
    @Override
    public long deleteByTree(UUID treeId) {
        return jdbc.update(
                "DELETE FROM report_snapshot WHERE tree_id = :t",
                new MapSqlParameterSource("t", treeId.toString()));
    }

    /**
     * Lưu (hoặc ghi đè nếu đã tồn tại) một bản chụp báo cáo.
     *
     * @param snapshot bản chụp cần lưu.
     */
    @Override
    public void save(ReportSnapshot snapshot) {
        jdbc.update(
                "INSERT INTO report_snapshot (id, tree_id, kind, payload, watermark, computed_at) "
                        + "VALUES (:id, :t, :k, :p, :w, :c) "
                        + "ON DUPLICATE KEY UPDATE payload = VALUES(payload), watermark = VALUES(watermark), computed_at = VALUES(computed_at)",
                new MapSqlParameterSource()
                        .addValue("id", snapshot.id().toString())
                        .addValue("t", snapshot.treeId().toString())
                        .addValue("k", snapshot.kind().name())
                        .addValue("p", snapshot.payload())
                        .addValue("w", snapshot.watermark())
                        .addValue("c", Timestamp.from(snapshot.computedAt())));
    }

    /**
     * Tìm một bản chụp báo cáo theo {@code id}.
     *
     * @param reportId định danh bản chụp.
     * @return {@code Optional} chứa bản chụp nếu tồn tại, ngược lại rỗng.
     */
    @Override
    public Optional<ReportSnapshot> findById(UUID reportId) {
        var rows = jdbc.queryForList(
                "SELECT id, tree_id, kind, payload, watermark, computed_at FROM report_snapshot WHERE id = :id",
                new MapSqlParameterSource("id", reportId.toString()));
        if (rows.isEmpty()) return Optional.empty();
        var r = rows.get(0);
        return Optional.of(new ReportSnapshot(
                UUID.fromString((String) r.get("id")),
                UUID.fromString((String) r.get("tree_id")),
                ReportSnapshot.Kind.valueOf((String) r.get("kind")),
                (String) r.get("payload"),
                ((Number) r.get("watermark")).longValue(),
                ((Timestamp) r.get("computed_at")).toInstant()));
    }

    /**
     * Kiểm tra đã có bản chụp dùng được (watermark {@code >=}) cho
     * {@code (treeId, kind)} chưa.
     *
     * @param treeId    định danh cây gia phả.
     * @param kind      loại báo cáo.
     * @param watermark mức watermark tối thiểu.
     * @return {@code true} nếu đã có.
     */
    @Override
    public boolean exists(UUID treeId, ReportSnapshot.Kind kind, long watermark) {
        var rows = jdbc.queryForList(
                "SELECT 1 FROM report_snapshot WHERE tree_id = :t AND kind = :k AND watermark >= :w LIMIT 1",
                new MapSqlParameterSource()
                        .addValue("t", treeId.toString())
                        .addValue("k", kind.name())
                        .addValue("w", watermark));
        return !rows.isEmpty();
    }
}
