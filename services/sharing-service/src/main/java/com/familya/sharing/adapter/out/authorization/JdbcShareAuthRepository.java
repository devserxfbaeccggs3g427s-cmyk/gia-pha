package com.familya.sharing.adapter.out.persistence;

import com.familya.sharing.application.port.out.ShareAuthRepository;
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
public class JdbcShareAuthRepository implements ShareAuthRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcShareAuthRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AuthRow> find(UUID treeId, UUID userId) {
        var rows = jdbc.queryForList(
                "SELECT tree_id, user_id, role, revision, epoch, revoked, last_updated_at "
                        + "FROM authorization_projection WHERE tree_id = :t AND user_id = :u",
                new MapSqlParameterSource()
                        .addValue("t", treeId.toString())
                        .addValue("u", userId.toString()));
        if (rows.isEmpty()) return Optional.empty();
        var r = rows.get(0);
        return Optional.of(new AuthRow(
                UUID.fromString((String) r.get("tree_id")),
                UUID.fromString((String) r.get("user_id")),
                (String) r.get("role"),
                ((Number) r.get("revision")).longValue(),
                ((Number) r.get("epoch")).longValue(),
                Boolean.TRUE.equals(r.get("revoked")),
                ((Timestamp) r.get("last_updated_at")).toInstant()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void upsert(UUID treeId, UUID userId, String role, long revision, long epoch,
                        Instant grantedAt, boolean revoked, String sourceEventId) {
        jdbc.update(
                "INSERT INTO authorization_projection (tree_id, user_id, role, revision, epoch, granted_at, revoked, source_event_id, last_updated_at) "
                        + "VALUES (:t, :u, :role, :rev, :ep, :at, :revoked, :src, :upd) "
                        + "ON DUPLICATE KEY UPDATE role = VALUES(role), revision = VALUES(revision), epoch = VALUES(epoch), "
                        + "revoked = VALUES(revoked), source_event_id = VALUES(source_event_id), last_updated_at = VALUES(last_updated_at)",
                new MapSqlParameterSource()
                        .addValue("t", treeId.toString())
                        .addValue("u", userId.toString())
                        .addValue("role", role)
                        .addValue("rev", revision)
                        .addValue("ep", epoch)
                        .addValue("at", Timestamp.from(grantedAt))
                        .addValue("revoked", revoked)
                        .addValue("src", sourceEventId)
                        .addValue("upd", Timestamp.from(Instant.now())));
    }
}
