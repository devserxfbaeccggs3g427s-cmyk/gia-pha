package com.familya.media.adapter.out.persistence;

import com.familya.media.application.port.out.MediaBinaryReplicationRepository;
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
public class JdbcMediaBinaryReplicationRepository implements MediaBinaryReplicationRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcMediaBinaryReplicationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID mediaId, String sourceRegion, String targetRegion, String sha256, String status, Instant at) {
        jdbc.update(
                "INSERT INTO media_binary_replication (id, media_id, source_region, target_region, sha256, status, last_attempt_at) "
                        + "VALUES (:id, :m, :s, :t, :h, :st, :at)",
                new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID().toString())
                        .addValue("m", mediaId.toString())
                        .addValue("s", sourceRegion)
                        .addValue("t", targetRegion)
                        .addValue("h", sha256)
                        .addValue("st", status)
                        .addValue("at", Timestamp.from(at)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReplicationRow> listForMedia(UUID mediaId) {
        var rows = jdbc.queryForList(
                "SELECT id, media_id, source_region, target_region, sha256, status, last_attempt_at "
                        + "FROM media_binary_replication WHERE media_id = :m",
                new MapSqlParameterSource("m", mediaId.toString()));
        return rows.stream().map(r -> new ReplicationRow(
                UUID.fromString((String) r.get("id")),
                UUID.fromString((String) r.get("media_id")),
                (String) r.get("source_region"),
                (String) r.get("target_region"),
                (String) r.get("sha256"),
                (String) r.get("status"),
                r.get("last_attempt_at") == null ? null : ((Timestamp) r.get("last_attempt_at")).toInstant())).toList();
    }
}
