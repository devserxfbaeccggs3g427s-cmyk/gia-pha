package com.familya.search.adapter.out.persistence;

import com.familya.search.application.port.out.WatermarkRepository;
import com.familya.search.domain.model.RevisionBarrier;
import com.familya.search.domain.model.Watermark;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Triển khai JDBC của {@link WatermarkRepository}, đọc/ghi bảng {@code watermark}.
 */
@Component
public class JdbcWatermarkRepository implements WatermarkRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Khởi tạo repository với JDBC template dùng chung.
     *
     * @param jdbc JDBC template dùng để truy vấn/lưu.
     */
    public JdbcWatermarkRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Tìm watermark cho một miền cụ thể của cây.
     *
     * @param treeId định danh cây gia phả.
     * @param domain miền dữ liệu.
     * @return {@code Optional} chứa {@link Watermark} nếu tồn tại.
     */
    @Override
    public Optional<Watermark> find(UUID treeId, Watermark.Domain domain) {
        var rows = jdbc.queryForList(
                "SELECT tree_id, domain, value, last_updated FROM watermark WHERE tree_id = :t AND domain = :d",
                new MapSqlParameterSource()
                        .addValue("t", treeId.toString())
                        .addValue("d", domain.name()));
        if (rows.isEmpty()) return Optional.empty();
        var r = rows.get(0);
        return Optional.of(new Watermark(
                UUID.fromString((String) r.get("tree_id")),
                Watermark.Domain.valueOf((String) r.get("domain")),
                ((Number) r.get("value")).longValue(),
                ((Timestamp) r.get("last_updated")).toInstant()));
    }

    /**
     * Lấy barrier phiên bản của cây - bản đồ từ miền sang watermark.
     *
     * @param treeId định danh cây gia phả.
     * @return {@link RevisionBarrier} chứa tất cả watermark của cây.
     */
    @Override
    public RevisionBarrier barrierFor(UUID treeId) {
        var rows = jdbc.queryForList(
                "SELECT domain, value FROM watermark WHERE tree_id = :t",
                new MapSqlParameterSource("t", treeId.toString()));
        Map<Watermark.Domain, Long> values = new EnumMap<>(Watermark.Domain.class);
        for (var r : rows) {
            values.put(Watermark.Domain.valueOf((String) r.get("domain")),
                    ((Number) r.get("value")).longValue());
        }
        return new RevisionBarrier(treeId, values);
    }

    /**
     * Nâng (hoặc giữ nguyên) watermark của một miền - chỉ tiến lên, không bao
     * giờ lùi nhờ {@code GREATEST(value, VALUES(value))}.
     *
     * @param treeId định danh cây gia phả.
     * @param domain miền dữ liệu.
     * @param value  giá trị watermark mới.
     */
    @Override
    public void advance(UUID treeId, Watermark.Domain domain, long value) {
        jdbc.update(
                "INSERT INTO watermark (tree_id, domain, value, last_updated) "
                        + "VALUES (:t, :d, :v, :upd) "
                        + "ON DUPLICATE KEY UPDATE value = GREATEST(value, VALUES(value)), last_updated = VALUES(last_updated)",
                new MapSqlParameterSource()
                        .addValue("t", treeId.toString())
                        .addValue("d", domain.name())
                        .addValue("v", value)
                        .addValue("upd", Timestamp.from(Instant.now())));
    }
}
