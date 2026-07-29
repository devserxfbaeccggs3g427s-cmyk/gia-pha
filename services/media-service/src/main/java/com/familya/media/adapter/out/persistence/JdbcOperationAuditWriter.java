package com.familya.media.adapter.out.persistence;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Minimal local projection writer used by the media service so that
 * {@code GET /api/v2/operations/{id}} resolves an envelope without
 * requiring a synchronous call into audit-ops. The row mirrors the
 * fields consumed by {@code OperationProjectionAdapter}.
 */
@Component
public class JdbcOperationAuditWriter {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcOperationAuditWriter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID operationId, UUID correlationId, UUID treeId, UUID actingUser,
                       String operationType, String aggregateType, String aggregateId,
                       String status, Instant now) {
        jdbc.update(
                "INSERT INTO operation_audit (id, correlation_id, service, operation_type, status, "
                        + "aggregate_type, aggregate_id, tree_id, acting_user, started_at, updated_at) "
                        + "VALUES (:id, :co, :svc, :type, :status, :at, :ai, :tree, :acting, :sa, :ua)",
                new MapSqlParameterSource()
                        .addValue("id", operationId.toString())
                        .addValue("co", correlationId == null ? operationId.toString() : correlationId.toString())
                        .addValue("svc", "media-service")
                        .addValue("type", operationType)
                        .addValue("status", status)
                        .addValue("at", aggregateType)
                        .addValue("ai", aggregateId)
                        .addValue("tree", treeId == null ? null : treeId.toString())
                        .addValue("acting", actingUser == null ? null : actingUser.toString())
                        .addValue("sa", Timestamp.from(now))
                        .addValue("ua", Timestamp.from(now)));
    }
}
