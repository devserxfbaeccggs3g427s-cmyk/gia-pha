package com.familya.sharing.adapter.out.projection;

import com.familya.sharing.application.port.out.AllowlistedProjectionRepository;
import com.familya.sharing.application.port.out.AllowlistedProjectionRepository.ShareScope;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class JdbcAllowlistedProjectionRepository implements AllowlistedProjectionRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public JdbcAllowlistedProjectionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public long readWatermark(UUID treeId, String domain) {
        var rows = jdbc.queryForList(
                "SELECT watermark FROM share_watermark WHERE tree_id = :t AND domain = :d",
                new MapSqlParameterSource().addValue("t", treeId.toString()).addValue("d", domain));
        if (rows.isEmpty()) return 0L;
        return ((Number) rows.get(0).get("watermark")).longValue();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public long advanceWatermark(UUID treeId, String domain, long watermark, Instant at) {
        jdbc.update(
                "INSERT INTO share_watermark (tree_id, domain, watermark, last_updated) "
                        + "VALUES (:t, :d, :w, :u) "
                        + "ON DUPLICATE KEY UPDATE watermark = GREATEST(watermark, VALUES(watermark)), last_updated = VALUES(last_updated)",
                new MapSqlParameterSource()
                        .addValue("t", treeId.toString())
                        .addValue("d", domain)
                        .addValue("w", watermark)
                        .addValue("u", Timestamp.from(at)));
        return watermark;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveMember(UUID treeId, UUID memberId, Map<String, Object> allowlisted, boolean tombstoned, Instant at) {
        upsert("share_member_projection", treeId, memberId, allowlisted, tombstoned, at);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveMedia(UUID treeId, UUID mediaId, Map<String, Object> allowlisted, boolean tombstoned, Instant at) {
        upsert("share_media_projection", treeId, mediaId, allowlisted, tombstoned, at);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveEvent(UUID treeId, UUID eventId, Map<String, Object> allowlisted, boolean tombstoned, Instant at) {
        upsert("share_event_projection", treeId, eventId, allowlisted, tombstoned, at);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveRelationship(UUID treeId, UUID relId, Map<String, Object> allowlisted, boolean tombstoned, Instant at) {
        upsert("share_relationship_projection", treeId, relId, allowlisted, tombstoned, at);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveTree(UUID treeId, Map<String, Object> allowlisted, boolean tombstoned, Instant at) {
        jdbc.update(
                "INSERT INTO share_tree_projection (tree_id, allowlisted, tombstoned, last_updated) "
                        + "VALUES (:t, :j, :tomb, :u) "
                        + "ON DUPLICATE KEY UPDATE allowlisted = VALUES(allowlisted), tombstoned = VALUES(tombstoned), last_updated = VALUES(last_updated)",
                params(treeId, null, allowlisted, tombstoned, at));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void savePublicProjection(UUID treeId, ShareScope scope, UUID targetId, Map<String, Object> value,
                                       long watermark, Instant at) {
        try {
            jdbc.update(
                    "INSERT INTO share_allowlisted_projection (tree_id, scope, target_id, allowlisted_json, watermark, last_updated) "
                            + "VALUES (:t, :s, :id, :j, :w, :u) "
                            + "ON DUPLICATE KEY UPDATE allowlisted_json = VALUES(allowlisted_json), watermark = VALUES(watermark), last_updated = VALUES(last_updated)",
                    new MapSqlParameterSource()
                            .addValue("t", treeId.toString())
                            .addValue("s", scope.name())
                            .addValue("id", targetId == null ? "" : targetId.toString())
                            .addValue("j", mapper.writeValueAsString(value))
                            .addValue("w", watermark)
                            .addValue("u", Timestamp.from(at)));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot serialize allowlisted projection", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> readPublicProjection(UUID treeId, ShareScope scope, UUID targetId) {
        var rows = jdbc.queryForList(
                "SELECT allowlisted_json FROM share_allowlisted_projection "
                        + "WHERE tree_id = :t AND scope = :s AND target_id <=> :id",
                new MapSqlParameterSource()
                        .addValue("t", treeId.toString())
                        .addValue("s", scope.name())
                        .addValue("id", targetId == null ? null : targetId.toString()));
        if (rows.isEmpty()) return Map.of();
        try {
            return mapper.readValue((String) rows.get(0).get("allowlisted_json"), new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Cannot deserialize allowlisted projection", e);
        }
    }

    private void upsert(String table, UUID treeId, UUID entityId, Map<String, Object> allowlisted,
                          boolean tombstoned, Instant at) {
        try {
            jdbc.update(
                    "INSERT INTO " + table + " (tree_id, " + idColumn(table) + ", allowlisted, tombstoned, last_updated) "
                            + "VALUES (:t, :id, :j, :tomb, :u) "
                            + "ON DUPLICATE KEY UPDATE allowlisted = VALUES(allowlisted), tombstoned = VALUES(tombstoned), last_updated = VALUES(last_updated)",
                    params(treeId, entityId, allowlisted, tombstoned, at));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot write projection", e);
        }
    }

    private MapSqlParameterSource params(UUID treeId, UUID entityId, Map<String, Object> allowlisted,
                                           boolean tombstoned, Instant at) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("t", treeId.toString())
                .addValue("id", entityId == null ? null : entityId.toString())
                .addValue("tomb", tombstoned)
                .addValue("u", Timestamp.from(at));
        try {
            p.addValue("j", mapper.writeValueAsString(allowlisted == null ? new HashMap<>() : allowlisted));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot serialize projection", e);
        }
        return p;
    }

    private static String idColumn(String table) {
        return switch (table) {
            case "share_member_projection" -> "member_id";
            case "share_media_projection" -> "media_id";
            case "share_event_projection" -> "event_id";
            case "share_relationship_projection" -> "relationship_id";
            default -> "id";
        };
    }
}
