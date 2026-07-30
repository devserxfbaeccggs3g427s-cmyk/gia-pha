package com.familya.auditops.adapter.out.persistence;

import com.familya.auditops.application.port.out.OperationLifecycleProjection;
import com.familya.auditops.domain.model.OperationLifecycleRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcOperationLifecycleProjection implements OperationLifecycleProjection {

    private final JdbcTemplate jdbc;

    public JdbcOperationLifecycleProjection(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void upsertStarted(OperationLifecycleRow row, String lastEventId) {
        jdbc.update("""
                INSERT INTO operation_lifecycle_projection
                    (operation_id, owner_service, saga_type, tree_id, initiating_user_id,
state, target_version, target_epoch, failure_code, failure_message, failure_routing,
                     started_at, updated_at, finalized_at, last_event_id)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE
                    owner_service = VALUES(owner_service),
                    saga_type = VALUES(saga_type),
                    tree_id = VALUES(tree_id),
                    initiating_user_id = VALUES(initiating_user_id),
                    state = VALUES(state),
                    target_version = VALUES(target_version),
                    target_epoch = VALUES(target_epoch),
                    failure_code = VALUES(failure_code),
                    failure_message = VALUES(failure_message),
                    failure_routing = VALUES(failure_routing),
                    updated_at = VALUES(updated_at),
                    finalized_at = COALESCE(finalized_at, VALUES(finalized_at)),
                    last_event_id = VALUES(last_event_id)
                """,
                row.operationId().toString(),
                row.ownerService(),
                row.sagaType(),
                row.treeId() == null ? null : row.treeId().toString(),
                row.initiatingUserId() == null ? null : row.initiatingUserId().toString(),
                row.state(),
                row.targetVersion(),
                row.targetEpoch(),
                row.failureCode(),
                row.failureMessage(),
                row.failureRouting(),
                Timestamp.from(row.startedAt()),
                Timestamp.from(row.updatedAt()),
                row.finalizedAt() == null ? null : Timestamp.from(row.finalizedAt()),
                lastEventId);
    }

    @Override
    public void applyStateChange(OperationLifecycleRow row, Instant finalizedAt,
                                 String lastEventId) {
        jdbc.update("""
                INSERT INTO operation_lifecycle_projection
                    (operation_id, owner_service, saga_type, tree_id, initiating_user_id,
                     state, target_version, target_epoch, failure_code, failure_message, failure_routing,
                     started_at, updated_at, finalized_at, last_event_id)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE
                    state = VALUES(state),
                    failure_code = VALUES(failure_code),
                    failure_message = VALUES(failure_message),
                    failure_routing = VALUES(failure_routing),
                    updated_at = VALUES(updated_at),
                    finalized_at = COALESCE(VALUES(finalized_at), finalized_at),
                    last_event_id = VALUES(last_event_id)
                """,
                row.operationId().toString(),
                row.ownerService(),
                row.sagaType(),
                row.treeId() == null ? null : row.treeId().toString(),
                row.initiatingUserId() == null ? null : row.initiatingUserId().toString(),
                row.state(),
                row.targetVersion(),
                row.targetEpoch(),
                row.failureCode(),
                row.failureMessage(),
                row.failureRouting(),
                Timestamp.from(row.startedAt()),
                Timestamp.from(row.updatedAt()),
                finalizedAt == null ? null : Timestamp.from(finalizedAt),
                lastEventId);
    }

    @Override
    public Optional<OperationLifecycleRow> find(UUID operationId) {
        var rows = jdbc.query("SELECT * FROM operation_lifecycle_projection WHERE operation_id = ?",
                MAPPER, operationId.toString());
        return rows.stream().findFirst();
    }

    private static final RowMapper<OperationLifecycleRow> MAPPER = (ResultSet rs, int n) -> new OperationLifecycleRow(
            UUID.fromString(rs.getString("operation_id")),
            rs.getString("owner_service"),
            rs.getString("saga_type"),
            rs.getString("tree_id") == null ? null : UUID.fromString(rs.getString("tree_id")),
            rs.getString("initiating_user_id") == null ? null : UUID.fromString(rs.getString("initiating_user_id")),
            rs.getString("state"),
            (Long) rs.getObject("target_version"),
            (Long) rs.getObject("target_epoch"),
            rs.getString("failure_code"),
            rs.getString("failure_message"),
            rs.getString("failure_routing"),
            rs.getTimestamp("started_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant(),
            rs.getTimestamp("finalized_at") == null ? null : rs.getTimestamp("finalized_at").toInstant());
}