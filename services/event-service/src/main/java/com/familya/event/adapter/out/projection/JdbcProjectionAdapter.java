package com.familya.event.adapter.out.projection;

import com.familya.event.application.port.out.EventAuthRepository;
import com.familya.event.application.port.out.ReferenceAvailability;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * JDBC adapter for the local projections (membership, member
 * existence, media existence).
 */
@Component
public class JdbcProjectionAdapter implements EventAuthRepository, ReferenceAvailability {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcProjectionAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // --- EventAuthRepository ---

    @Override
    public Optional<EventAuthRepository.EventAuthRow> findAuth(UUID treeId, UUID userId) {
        var rows = jdbc.queryForList(
                "SELECT tree_id, user_id, role, revision, epoch, granted_at, revoked, source_event_id, last_updated_at "
                        + "FROM authorization_projection WHERE tree_id = :t AND user_id = :u",
                new MapSqlParameterSource().addValue("t", treeId.toString()).addValue("u", userId.toString()));
        if (rows.isEmpty()) return Optional.empty();
        var r = rows.get(0);
        return Optional.of(new EventAuthRepository.EventAuthRow(
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
    public void upsertAuth(EventAuthRepository.EventAuthRow row) {
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

    // --- ReferenceAvailability ---

    @Override
    public boolean isMemberAvailable(UUID treeId, UUID memberId) {
        var rows = jdbc.queryForList(
                "SELECT exists, tombstoned FROM member_reference_projection "
                        + "WHERE tree_id = :t AND member_id = :m",
                new MapSqlParameterSource().addValue("t", treeId.toString()).addValue("m", memberId.toString()));
        if (rows.isEmpty()) return false;
        return Boolean.TRUE.equals(rows.get(0).get("exists"))
                && !Boolean.TRUE.equals(rows.get(0).get("tombstoned"));
    }

    @Override
    public boolean isMediaAvailable(UUID treeId, UUID mediaId) {
        var rows = jdbc.queryForList(
                "SELECT exists, tombstoned FROM media_reference_projection "
                        + "WHERE tree_id = :t AND media_id = :m",
                new MapSqlParameterSource().addValue("t", treeId.toString()).addValue("m", mediaId.toString()));
        if (rows.isEmpty()) return false;
        return Boolean.TRUE.equals(rows.get(0).get("exists"))
                && !Boolean.TRUE.equals(rows.get(0).get("tombstoned"));
    }

    @Override
    public Set<UUID> danglingMembers(UUID treeId, Collection<UUID> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) return Set.of();
        return memberIds.stream()
                .filter(id -> id != null && !isMemberAvailable(treeId, id))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public Set<UUID> danglingMedia(UUID treeId, Collection<UUID> mediaIds) {
        if (mediaIds == null || mediaIds.isEmpty()) return Set.of();
        return mediaIds.stream()
                .filter(id -> id != null && !isMediaAvailable(treeId, id))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}