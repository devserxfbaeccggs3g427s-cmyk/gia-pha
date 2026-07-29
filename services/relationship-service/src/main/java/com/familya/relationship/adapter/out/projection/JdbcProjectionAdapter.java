package com.familya.relationship.adapter.out.projection;

import com.familya.relationship.application.port.out.AuthorizationProjectionRepository;
import com.familya.relationship.application.port.out.MemberExistenceProjection;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * JDBC-backed adapter for the local authorization projection and the
 * member-existence projection. The {@code member_existence_projection}
 * table is fed by the {@code Member} service's event stream.
 */
@Component
public class JdbcProjectionAdapter implements AuthorizationProjectionRepository, MemberExistenceProjection {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcProjectionAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // --- AuthorizationProjectionRepository ---

    @Override
    public Optional<AuthorizationProjectionRepository.RelationshipAuthRow> findAuth(UUID treeId, UUID userId) {
        var rows = jdbc.queryForList(
                "SELECT tree_id, user_id, role, revision, epoch, granted_at, revoked, source_event_id, last_updated_at "
                        + "FROM authorization_projection WHERE tree_id = :t AND user_id = :u",
                new MapSqlParameterSource().addValue("t", treeId.toString()).addValue("u", userId.toString()));
        if (rows.isEmpty()) return Optional.empty();
        var r = rows.get(0);
        return Optional.of(new AuthorizationProjectionRepository.RelationshipAuthRow(
                UUID.fromString((String) r.get("tree_id")),
                UUID.fromString((String) r.get("user_id")),
                (String) r.get("role"),
                ((Number) r.get("revision")).longValue(),
                ((Number) r.get("epoch")).longValue(),
                ((java.sql.Timestamp) r.get("granted_at")).toInstant(),
                Boolean.TRUE.equals(r.get("revoked")),
                (String) r.get("source_event_id"),
                ((java.sql.Timestamp) r.get("last_updated_at")).toInstant()));
    }

    @Override
    public void upsertAuth(AuthorizationProjectionRepository.RelationshipAuthRow row) {
        jdbc.update(
                "INSERT INTO authorization_projection "
                        + "(tree_id, user_id, role, revision, epoch, granted_at, revoked, source_event_id, last_updated_at) "
                        + "VALUES (:t, :u, :role, :rev, :epoch, :at, :revoked, :src, :upd) "
                        + "ON DUPLICATE KEY UPDATE role = VALUES(role), revision = VALUES(revision), "
                        + "epoch = VALUES(epoch), revoked = VALUES(revoked), "
                        + "source_event_id = VALUES(source_event_id), last_updated_at = VALUES(last_updated_at)",
                new MapSqlParameterSource()
                        .addValue("t", row.treeId().toString())
                        .addValue("u", row.userId().toString())
                        .addValue("role", row.role())
                        .addValue("rev", row.revision())
                        .addValue("epoch", row.epoch())
                        .addValue("at", java.sql.Timestamp.from(row.grantedAt()))
                        .addValue("revoked", row.revoked())
                        .addValue("src", row.sourceEventId())
                        .addValue("upd", java.sql.Timestamp.from(row.lastUpdatedAt())));
    }

    // --- MemberExistenceProjection ---

    @Override
    public boolean isAvailable(UUID treeId, UUID memberId) {
        var rows = jdbc.queryForList(
                "SELECT exists, tombstoned FROM member_existence_projection "
                        + "WHERE tree_id = :t AND member_id = :m",
                new MapSqlParameterSource().addValue("t", treeId.toString()).addValue("m", memberId.toString()));
        if (rows.isEmpty()) return false;
        Boolean exists = (Boolean) rows.get(0).get("exists");
        Boolean tomb = (Boolean) rows.get(0).get("tombstoned");
        return Boolean.TRUE.equals(exists) && !Boolean.TRUE.equals(tomb);
    }

    @Override
    public Optional<Boolean> tombstone(UUID treeId, UUID memberId) {
        var rows = jdbc.queryForList(
                "SELECT tombstoned FROM member_existence_projection "
                        + "WHERE tree_id = :t AND member_id = :m",
                new MapSqlParameterSource().addValue("t", treeId.toString()).addValue("m", memberId.toString()));
        if (rows.isEmpty()) return Optional.empty();
        return Optional.of(Boolean.TRUE.equals(rows.get(0).get("tombstoned")));
    }
}