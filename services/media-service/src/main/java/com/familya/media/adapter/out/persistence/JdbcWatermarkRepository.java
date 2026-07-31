package com.familya.media.adapter.out.persistence;

import com.familya.media.application.port.out.MediaWatermarkRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter đầu ra (outbound) — repository watermark theo (treeId, domain).
 * <p>
 * Watermark dùng để theo dõi "điểm tiến" của từng miền dữ liệu trong cây,
 * phục vụ reconcile/backfill.
 */
@Component
public class JdbcWatermarkRepository implements MediaWatermarkRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Khởi tạo repository.
     *
     * @param jdbc JDBC template.
     */
    public JdbcWatermarkRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Đọc watermark hiện tại cho (tree, domain).
     *
     * @param treeId UUID cây.
     * @param domain miền dữ liệu (vd "media").
     * @return Optional chứa mảng 1 phần tử long[watermark], empty nếu chưa có.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<long[]> read(UUID treeId, String domain) {
        var rows = jdbc.queryForList(
                "SELECT watermark FROM media_watermark WHERE tree_id = :t AND domain = :d",
                new MapSqlParameterSource().addValue("t", treeId.toString()).addValue("d", domain));
        if (rows.isEmpty()) return Optional.empty();
        return Optional.of(new long[]{ ((Number) rows.get(0).get("watermark")).longValue() });
    }

    /**
     * Nâng watermark cho (tree, domain).
     * <p>
     * Dùng {@code GREATEST} để watermark chỉ đi lên, không bao giờ tụt về
     * giá trị cũ khi replay manifest cũ.
     *
     * @param treeId       UUID cây.
     * @param domain       miền dữ liệu.
     * @param newWatermark giá trị watermark mới.
     * @param now          thời điểm.
     * @return giá trị {@code newWatermark} đã được áp dụng.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public long advance(UUID treeId, String domain, long newWatermark, Instant now) {
        // GREATEST chống tụt watermark khi nhận manifest cũ / out-of-order.
        jdbc.update(
                "INSERT INTO media_watermark (tree_id, domain, watermark, last_updated) "
                        + "VALUES (:t, :d, :w, :upd) "
                        + "ON DUPLICATE KEY UPDATE watermark = GREATEST(watermark, VALUES(watermark)), "
                        + "last_updated = VALUES(last_updated)",
                new MapSqlParameterSource()
                        .addValue("t", treeId.toString())
                        .addValue("d", domain)
                        .addValue("w", newWatermark)
                        .addValue("upd", Timestamp.from(now)));
        return newWatermark;
    }
}
