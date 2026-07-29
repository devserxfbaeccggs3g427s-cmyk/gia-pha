package com.familya.auditops.adapter.out.persistence;

import com.familya.auditops.domain.model.Operation;
import com.familya.auditops.domain.model.OperationStatus;
import com.familya.auditops.application.port.out.OperationRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
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
public class JdbcOperationRepository implements OperationRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcOperationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Operation insert(Operation op) {
        jdbc.update(
                "INSERT INTO operation_audit "
                        + "(id, correlation_id, service, operation_type, status, target_revision, target_epoch, "
                        + " aggregate_type, aggregate_id, tree_id, acting_user, detail_json, error_code, error_message, "
                        + " started_at, updated_at, finished_at, version) "
                        + "VALUES (:id, :co, :svc, :type, :status, :tr, :te, :at, :ai, :tree, :acting, :detail, :ec, :em, "
                        + " :sa, :ua, :fa, :v)",
                params(op));
        return op;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Operation> findById(UUID operationId) {
        var rows = jdbc.queryForList(
                "SELECT * FROM operation_audit WHERE id = :id",
                new MapSqlParameterSource("id", operationId.toString()));
        if (rows.isEmpty()) return Optional.empty();
        return Optional.of(fromRow(rows.get(0)));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Operation transition(UUID operationId,
                                long expectedVersion,
                                OperationStatus next,
                                String errorCode,
                                String errorMessage,
                                Instant when) {
        int updated = jdbc.update(
                "UPDATE operation_audit "
                        + "SET status = :status, error_code = :ec, error_message = :em, "
                        + "    updated_at = :ua, "
                        + "    finished_at = CASE WHEN :terminal = 1 THEN :ua ELSE finished_at END, "
                        + "    version = version + 1 "
                        + "WHERE id = :id AND version = :ev",
                new MapSqlParameterSource()
                        .addValue("status", next.name())
                        .addValue("ec", errorCode)
                        .addValue("em", errorMessage)
                        .addValue("ua", Timestamp.from(when))
                        .addValue("terminal", next.isTerminal() ? 1 : 0)
                        .addValue("id", operationId.toString())
                        .addValue("ev", expectedVersion));
        if (updated == 0) {
            throw new com.familya.platform.error.OptimisticConcurrencyException(
                    "Operation " + operationId + " was modified concurrently.");
        }
        return findById(operationId).orElseThrow();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Operation> findByStatus(OperationStatus status, int limit) {
        var rows = jdbc.queryForList(
                "SELECT * FROM operation_audit WHERE status = :s ORDER BY updated_at DESC LIMIT :l",
                new MapSqlParameterSource()
                        .addValue("s", status.name())
                        .addValue("l", limit));
        return rows.stream().map(this::fromRow).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countByStatusIn(List<OperationStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) return 0L;
        var inClause = String.join(",", statuses.stream().map(s -> "'" + s.name() + "'").toList());
        var rows = jdbc.queryForList(
                "SELECT COUNT(1) AS c FROM operation_audit WHERE status IN (" + inClause + ")",
                new MapSqlParameterSource());
        return rows.isEmpty() ? 0L : ((Number) rows.get(0).get("c")).longValue();
    }

    private MapSqlParameterSource params(Operation op) {
        return new MapSqlParameterSource()
                .addValue("id", op.id().toString())
                .addValue("co", op.correlationId() == null ? null : op.correlationId().toString())
                .addValue("svc", op.service())
                .addValue("type", op.operationType())
                .addValue("status", op.status().name())
                .addValue("tr", op.targetRevision())
                .addValue("te", op.targetEpoch())
                .addValue("at", op.aggregateType())
                .addValue("ai", op.aggregateId())
                .addValue("tree", op.treeId() == null ? null : op.treeId().toString())
                .addValue("acting", op.actingUser() == null ? null : op.actingUser().toString())
                .addValue("detail", json(op.detail()))
                .addValue("ec", op.errorCode())
                .addValue("em", op.errorMessage())
                .addValue("sa", Timestamp.from(op.startedAt()))
                .addValue("ua", Timestamp.from(op.updatedAt()))
                .addValue("fa", op.finishedAt() == null ? null : Timestamp.from(op.finishedAt()))
                .addValue("v", op.version());
    }

    private Operation fromRow(Map<String, Object> r) {
        return new Operation(
                UUID.fromString((String) r.get("id")),
                r.get("correlation_id") == null ? null : UUID.fromString((String) r.get("correlation_id")),
                (String) r.get("service"),
                (String) r.get("operation_type"),
                OperationStatus.valueOf((String) r.get("status")),
                r.get("target_revision") == null ? null : ((Number) r.get("target_revision")).longValue(),
                r.get("target_epoch") == null ? null : ((Number) r.get("target_epoch")).longValue(),
                (String) r.get("aggregate_type"),
                (String) r.get("aggregate_id"),
                r.get("tree_id") == null ? null : UUID.fromString((String) r.get("tree_id")),
                r.get("acting_user") == null ? null : UUID.fromString((String) r.get("acting_user")),
                Map.of(),
                (String) r.get("error_code"),
                (String) r.get("error_message"),
                ((Timestamp) r.get("started_at")).toInstant(),
                ((Timestamp) r.get("updated_at")).toInstant(),
                r.get("finished_at") == null ? null : ((Timestamp) r.get("finished_at")).toInstant(),
                ((Number) r.get("version")).longValue());
    }

    private static String json(Map<String, Object> m) {
        if (m == null || m.isEmpty()) return null;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(m);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot serialise detail", e);
        }
    }
}