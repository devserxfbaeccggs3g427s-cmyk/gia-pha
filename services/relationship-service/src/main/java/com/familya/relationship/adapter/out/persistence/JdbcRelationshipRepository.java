package com.familya.relationship.adapter.out.persistence;

import com.familya.relationship.application.port.out.RelationshipRepository;
import com.familya.relationship.domain.model.Relationship;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JdbcRelationshipRepository implements RelationshipRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcRelationshipRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(Relationship rel) {
        jdbc.update(
                "INSERT INTO relationship (id, tree_id, kind, from_member_id, to_member_id, "
                        + "metadata_json, revision, created_at, version) "
                        + "VALUES (:id, :tree, :kind, :from, :to, :meta, :rev, :created, :v)",
                params(rel));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Relationship> findById(UUID id) {
        var rows = jdbc.queryForList(
                "SELECT id, tree_id, kind, from_member_id, to_member_id, metadata_json, "
                        + "revision, created_at, tombstoned_at, version "
                        + "FROM relationship WHERE id = :id",
                new MapSqlParameterSource("id", id.toString()));
        return rows.isEmpty() ? Optional.empty() : Optional.of(fromRow(rows.get(0)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Relationship> listByTree(UUID treeId, boolean includeTombstoned) {
        String sql = includeTombstoned
                ? "SELECT * FROM relationship WHERE tree_id = :t"
                : "SELECT * FROM relationship WHERE tree_id = :t AND tombstoned_at IS NULL";
        var rows = jdbc.queryForList(sql, new MapSqlParameterSource("t", treeId.toString()));
        return rows.stream().map(this::fromRow).toList();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public long nextCommandSeq(UUID treeId) {
        // Per-tree monotonic sequence. We rely on MySQL row-level
        // locking via SELECT … FOR UPDATE inside the transaction; this
        // serializes concurrent graph commands for the same tree
        // while letting different trees proceed in parallel.
        try {
            jdbc.update(
                    "INSERT INTO graph_command_log (tree_id, command_seq, command_type, actor_user_id, payload_hash, committed_at) "
                            + "VALUES (:t, 1, 'init', :u, 'init', :ts)",
                    new MapSqlParameterSource()
                            .addValue("t", treeId.toString())
                            .addValue("u", "00000000-0000-0000-0000-000000000000")
                            .addValue("ts", Timestamp.from(java.time.Instant.now())));
        } catch (Exception ignored) { /* already initialized */ }
        Long last = jdbc.queryForObject(
                "SELECT MAX(command_seq) FROM graph_command_log WHERE tree_id = :t FOR UPDATE",
                new MapSqlParameterSource("t", treeId.toString()), Long.class);
        return (last == null ? 0L : last) + 1L;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendCommandLog(UUID treeId, long commandSeq, String commandType,
                                  UUID actorUserId, String payloadHash, java.time.Instant committedAt) {
        jdbc.update(
                "INSERT INTO graph_command_log (tree_id, command_seq, command_type, actor_user_id, payload_hash, committed_at) "
                        + "VALUES (:t, :seq, :type, :u, :h, :ts)",
                new MapSqlParameterSource()
                        .addValue("t", treeId.toString())
                        .addValue("seq", commandSeq)
                        .addValue("type", commandType)
                        .addValue("u", actorUserId.toString())
                        .addValue("h", payloadHash)
                        .addValue("ts", Timestamp.from(committedAt)));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void update(Relationship rel) {
        jdbc.update(
                "UPDATE relationship SET tombstoned_at = :tomb, version = :v WHERE id = :id",
                new MapSqlParameterSource()
                        .addValue("tomb", rel.tombstonedAt() == null ? null : Timestamp.from(rel.tombstonedAt()))
                        .addValue("v", rel.version())
                        .addValue("id", rel.id().toString()));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsEdge(UUID treeId, Relationship.Kind kind, UUID from, UUID to) {
        Integer n;
        try {
            n = jdbc.queryForObject(
                    "SELECT COUNT(1) FROM relationship "
                            + "WHERE tree_id = :t AND kind = :k AND from_member_id = :f AND to_member_id = :to",
                    new MapSqlParameterSource()
                            .addValue("t", treeId.toString())
                            .addValue("k", kind.name())
                            .addValue("f", from.toString())
                            .addValue("to", to.toString()),
                    Integer.class);
        } catch (EmptyResultDataAccessException e) { return false; }
        return n != null && n > 0;
    }

    private MapSqlParameterSource params(Relationship r) {
        return new MapSqlParameterSource()
                .addValue("id", r.id().toString())
                .addValue("tree", r.treeId().toString())
                .addValue("kind", r.kind().name())
                .addValue("from", r.fromMemberId().toString())
                .addValue("to", r.toMemberId().toString())
                .addValue("meta", r.metadataJson())
                .addValue("rev", r.revision())
                .addValue("created", Timestamp.from(r.createdAt()))
                .addValue("v", r.version());
    }

    private Relationship fromRow(java.util.Map<String, Object> r) {
        return new Relationship(
                UUID.fromString((String) r.get("id")),
                UUID.fromString((String) r.get("tree_id")),
                Relationship.Kind.valueOf((String) r.get("kind")),
                UUID.fromString((String) r.get("from_member_id")),
                UUID.fromString((String) r.get("to_member_id")),
                (String) r.get("metadata_json"),
                ((Number) r.get("revision")).longValue(),
                ((Timestamp) r.get("created_at")).toInstant(),
                r.get("tombstoned_at") == null ? null : ((Timestamp) r.get("tombstoned_at")).toInstant(),
                ((Number) r.get("version")).longValue());
    }
}