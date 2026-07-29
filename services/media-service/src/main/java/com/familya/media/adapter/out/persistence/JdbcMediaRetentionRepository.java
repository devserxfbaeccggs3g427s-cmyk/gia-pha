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

@Component
public class JdbcMediaRetentionRepository implements MediaRetentionRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcMediaRetentionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void placeHold(UUID mediaId, UUID treeId, Instant holdUntil, String reason) {
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

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(UUID holdId, Instant releasedAt) {
        jdbc.update(
                "UPDATE media_retention_hold SET released_at = :r WHERE id = :id",
                new MapSqlParameterSource()
                        .addValue("r", Timestamp.from(releasedAt))
                        .addValue("id", holdId.toString()));
    }

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
