package com.familya.treeaccess.adapter.out.persistence;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Writer cho bảng {@code operation_audit} của Tree Access service.
 *
 * <p>Mỗi mutation Saga khởi tạo ghi một row tại đây trong cùng transaction
 * với Saga state và outbox. Polling endpoint đọc row này để trả
 * {@link com.familya.platform.api.AsyncOperation} cho client.</p>
 */
@Component
public class JdbcOperationAuditWriter {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcOperationAuditWriter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordInitiated(UUID operationId, UUID correlationId, UUID treeId, UUID actingUser,
                                UUID aggregateId, String operationType,
                                long targetRevision, long targetEpoch,
                                Instant now) {
        jdbc.update("""
                INSERT INTO operation_audit
                    (id, correlation_id, service, operation_type, status, target_revision, target_epoch,
                     aggregate_type, aggregate_id, tree_id, acting_user, started_at, updated_at, version)
                VALUES (:id, :co, :svc, :type, 'PENDING', :tr, :te, 'tree', :ai, :tree, :acting, :sa, :ua, 0)
                """,
                new MapSqlParameterSource()
                        .addValue("id", operationId.toString())
                        .addValue("co", correlationId == null ? operationId.toString() : correlationId.toString())
                        .addValue("svc", "tree-access-service")
                        .addValue("type", operationType)
                        .addValue("tr", targetRevision)
                        .addValue("te", targetEpoch)
                        .addValue("ai", aggregateId == null ? null : aggregateId.toString())
                        .addValue("tree", treeId == null ? null : treeId.toString())
                        .addValue("acting", actingUser == null ? null : actingUser.toString())
                        .addValue("sa", Timestamp.from(now))
                        .addValue("ua", Timestamp.from(now)));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void transition(UUID operationId, long expectedVersion, String newStatus,
                           Long appliedRevision, Long appliedEpoch, String errorCode,
                           String errorMessage, String failureRouting, Instant when) {
        int updated = jdbc.update("""
                UPDATE operation_audit
                   SET status = :status,
                       error_code = :ec,
                       error_message = :em,
                       failure_routing = :fr,
                       target_applied_revision = :ar,
                       target_applied_epoch = :ae,
                       updated_at = :ua,
                       finished_at = CASE WHEN :terminal = 1 THEN :ua ELSE finished_at END,
                       version = version + 1
                 WHERE id = :id AND version = :ev
                """,
                new MapSqlParameterSource()
                        .addValue("status", newStatus)
                        .addValue("ec", errorCode)
                        .addValue("em", errorMessage)
                        .addValue("fr", failureRouting)
                        .addValue("ar", appliedRevision)
                        .addValue("ae", appliedEpoch)
                        .addValue("ua", Timestamp.from(when))
                        .addValue("terminal", isTerminal(newStatus) ? 1 : 0)
                        .addValue("id", operationId.toString())
                        .addValue("ev", expectedVersion));
        if (updated == 0) {
            throw new com.familya.platform.error.OptimisticConcurrencyException(
                    "Operation " + operationId + " was modified concurrently.");
        }
    }

    private static boolean isTerminal(String status) {
        return "SUCCEEDED".equals(status) || "FAILED".equals(status)
                || "COMPENSATED".equals(status) || "MANUAL_REVIEW".equals(status);
    }
}
