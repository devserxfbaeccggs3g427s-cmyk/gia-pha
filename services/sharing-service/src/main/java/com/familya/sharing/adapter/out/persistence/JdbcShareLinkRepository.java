package com.familya.sharing.adapter.out.persistence;

import com.familya.sharing.application.port.out.ShareLinkRepository;
import com.familya.sharing.domain.model.ShareLink;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class JdbcShareLinkRepository implements ShareLinkRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcShareLinkRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(ShareLink l) {
        jdbc.update(
                "INSERT INTO share_link (id, tree_id, scope, target_id, role, token_hash, created_by_user_id, "
                        + "created_at, expires_at, revoked_at, revocation_reason, revision, version) "
                        + "VALUES (:id, :tree, :scope, :target, :role, :hash, :creator, :created, :expires, :revoked, :reason, :revision, :version)",
                params(l));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void update(ShareLink l) {
        jdbc.update(
                "UPDATE share_link SET revoked_at = :revoked, revocation_reason = :reason, revision = :revision, version = :version WHERE id = :id",
                params(l));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ShareLink> findById(UUID id) {
        return one("SELECT * FROM share_link WHERE id = :x", Map.of("x", id.toString()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ShareLink> findByTokenHash(String hash) {
        return one("SELECT * FROM share_link WHERE token_hash = :x", Map.of("x", hash));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShareLink> listByTree(UUID id) {
        var rows = jdbc.queryForList(
                "SELECT * FROM share_link WHERE tree_id = :x ORDER BY created_at DESC",
                Map.of("x", id.toString()));
        return rows.stream().map(this::row).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShareLink> listActiveByScope(UUID treeId, ShareLink.Scope scope, UUID targetId) {
        var rows = jdbc.queryForList(
                "SELECT * FROM share_link WHERE tree_id = :t AND scope = :s AND target_id <=> :id "
                        + "AND revoked_at IS NULL ORDER BY created_at DESC",
                new MapSqlParameterSource()
                        .addValue("t", treeId.toString())
                        .addValue("s", scope.name())
                        .addValue("id", targetId == null ? null : targetId.toString()));
        return rows.stream().map(this::row).toList();
    }

    private Optional<ShareLink> one(String sql, Map<String, Object> params) {
        var rows = jdbc.queryForList(sql, params);
        return rows.isEmpty() ? Optional.empty() : Optional.of(row(rows.get(0)));
    }

    private MapSqlParameterSource params(ShareLink l) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("id", l.id().toString())
                .addValue("tree", l.treeId().toString())
                .addValue("scope", l.scope().name())
                .addValue("target", l.targetId() == null ? null : l.targetId().toString())
                .addValue("role", l.role().name())
                .addValue("hash", l.tokenHash())
                .addValue("creator", l.createdByUserId().toString())
                .addValue("created", Timestamp.from(l.createdAt()))
                .addValue("expires", l.expiresAt() == null ? null : Timestamp.from(l.expiresAt()))
                .addValue("revoked", l.revokedAt() == null ? null : Timestamp.from(l.revokedAt()))
                .addValue("reason", l.revocationReason())
                .addValue("revision", l.revision())
                .addValue("version", l.version());
        return p;
    }

    private ShareLink row(Map<String, Object> r) {
        return new ShareLink(
                UUID.fromString((String) r.get("id")),
                UUID.fromString((String) r.get("tree_id")),
                ShareLink.Scope.valueOf((String) r.get("scope")),
                r.get("target_id") == null ? null : UUID.fromString((String) r.get("target_id")),
                ShareLink.Role.valueOf((String) r.get("role")),
                (String) r.get("token_hash"),
                UUID.fromString((String) r.get("created_by_user_id")),
                ((Timestamp) r.get("created_at")).toInstant(),
                r.get("expires_at") == null ? null : ((Timestamp) r.get("expires_at")).toInstant(),
                r.get("revoked_at") == null ? null : ((Timestamp) r.get("revoked_at")).toInstant(),
                (String) r.get("revocation_reason"),
                ((Number) r.get("revision")).longValue(),
                ((Number) r.get("version")).longValue());
    }
}
