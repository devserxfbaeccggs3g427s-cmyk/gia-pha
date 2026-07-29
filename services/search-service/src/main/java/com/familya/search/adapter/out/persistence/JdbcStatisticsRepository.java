package com.familya.search.adapter.out.persistence;

import com.familya.search.application.port.out.StatisticsRepository;
import com.familya.search.domain.model.StatisticsSnapshot;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class JdbcStatisticsRepository implements StatisticsRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcStatisticsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long deleteByTree(UUID treeId) {
        return jdbc.update(
                "DELETE FROM statistics_snapshot WHERE tree_id = :t",
                new MapSqlParameterSource("t", treeId.toString()));
    }

    @Override
    public StatisticsSnapshot compute(UUID treeId, long watermark) {
        long members = count(treeId, "search_member_doc");
        long events = count(treeId, "search_event_doc");
        long media = count(treeId, "search_media_doc");
        Integer generationsBoxed = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT generation) FROM member_generation_projection WHERE tree_id = :t",
                new MapSqlParameterSource("t", treeId.toString()), Integer.class);
        int generations = generationsBoxed == null ? 0 : generationsBoxed;
        Instant now = Instant.now();
        StatisticsSnapshot snap = new StatisticsSnapshot(treeId, members, generations, events, media, now, watermark);
        jdbc.update(
                "INSERT INTO statistics_snapshot (tree_id, member_count, generations, events_count, media_count, watermark, computed_at) "
                        + "VALUES (:t, :m, :g, :e, :md, :w, :c) "
                        + "ON DUPLICATE KEY UPDATE member_count = VALUES(member_count), generations = VALUES(generations), "
                        + "events_count = VALUES(events_count), media_count = VALUES(media_count), "
                        + "watermark = VALUES(watermark), computed_at = VALUES(computed_at)",
                new MapSqlParameterSource()
                        .addValue("t", treeId.toString())
                        .addValue("m", members)
                        .addValue("g", generations)
                        .addValue("e", events)
                        .addValue("md", media)
                        .addValue("w", watermark)
                        .addValue("c", Timestamp.from(now)));
        return snap;
    }

    @Override
    public Optional<StatisticsSnapshot> latest(UUID treeId) {
        var rows = jdbc.queryForList(
                "SELECT tree_id, member_count, generations, events_count, media_count, watermark, computed_at "
                        + "FROM statistics_snapshot WHERE tree_id = :t",
                new MapSqlParameterSource("t", treeId.toString()));
        if (rows.isEmpty()) return Optional.empty();
        var r = rows.get(0);
        return Optional.of(new StatisticsSnapshot(
                UUID.fromString((String) r.get("tree_id")),
                ((Number) r.get("member_count")).longValue(),
                ((Number) r.get("generations")).intValue(),
                ((Number) r.get("events_count")).longValue(),
                ((Number) r.get("media_count")).longValue(),
                ((Timestamp) r.get("computed_at")).toInstant(),
                ((Number) r.get("watermark")).longValue()));
    }

    private long count(UUID treeId, String table) {
        Long c = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE tree_id = :t AND tombstoned = FALSE",
                new MapSqlParameterSource("t", treeId.toString()), Long.class);
        return c == null ? 0L : c;
    }
}
