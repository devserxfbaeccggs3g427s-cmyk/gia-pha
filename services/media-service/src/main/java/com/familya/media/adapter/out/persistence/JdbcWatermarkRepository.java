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

@Component
public class JdbcWatermarkRepository implements MediaWatermarkRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcWatermarkRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<long[]> read(UUID treeId, String domain) {
        var rows = jdbc.queryForList(
                "SELECT watermark FROM media_watermark WHERE tree_id = :t AND domain = :d",
                new MapSqlParameterSource().addValue("t", treeId.toString()).addValue("d", domain));
        if (rows.isEmpty()) return Optional.empty();
        return Optional.of(new long[]{ ((Number) rows.get(0).get("watermark")).longValue() });
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public long advance(UUID treeId, String domain, long newWatermark, Instant now) {
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
