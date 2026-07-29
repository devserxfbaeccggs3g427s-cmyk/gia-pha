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

@Component
public class JdbcMediaReferenceRepository implements MediaReferenceRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcMediaReferenceRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void upsert(UUID mediaId, UUID treeId, String targetKind, UUID targetId,
                        String status, Instant at, String errorCode) {
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

    @Override
    @Transactional(readOnly = true)
    public List<ReferenceRow> listForMedia(UUID mediaId) {
        var rows = jdbc.queryForList(
                "SELECT media_id, tree_id, target_kind, target_id, status, last_attempt_at, last_error_code "
                        + "FROM media_reference WHERE media_id = :m",
                new MapSqlParameterSource("m", mediaId.toString()));
        return rows.stream().map(this::fromRow).toList();
    }

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
