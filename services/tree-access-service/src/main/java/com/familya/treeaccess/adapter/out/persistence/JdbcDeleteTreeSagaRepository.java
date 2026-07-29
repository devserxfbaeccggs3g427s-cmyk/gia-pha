package com.familya.treeaccess.adapter.out.persistence;

import com.familya.treeaccess.application.port.out.DeleteTreeSagaRepository;
import com.familya.treeaccess.domain.model.DeleteTreeSagaState;
import com.familya.treeaccess.domain.model.DeleteTreeSagaStep;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcDeleteTreeSagaRepository implements DeleteTreeSagaRepository {

    private final JdbcTemplate jdbc;

    public JdbcDeleteTreeSagaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void saveState(DeleteTreeSagaState s) {
        jdbc.update("""
                INSERT INTO delete_tree_saga_state
                    (operation_id, tree_id, initiating_user_id, correlation_id,
                     state, target_aggregate_version, target_epoch,
                     deadline_at, started_at, finalized_at, last_updated_at, irreversible_at,
                     failure_code, failure_message)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE
                    state = VALUES(state),
                    finalized_at = VALUES(finalized_at),
                    last_updated_at = VALUES(last_updated_at),
                    irreversible_at = VALUES(irreversible_at),
                    failure_code = VALUES(failure_code),
                    failure_message = VALUES(failure_message)
                """, ps -> bindState(ps, s));
    }

    @Override
    public Optional<DeleteTreeSagaState> findState(UUID operationId) {
        var rows = jdbc.query("SELECT * FROM delete_tree_saga_state WHERE operation_id = ?",
                STATE_MAPPER, operationId.toString());
        return rows.stream().findFirst();
    }

    @Override
    public void saveSteps(List<DeleteTreeSagaStep> steps) {
        if (steps.isEmpty()) return;
        jdbc.batchUpdate("""
                INSERT INTO delete_tree_saga_step
                    (operation_id, sequence_no, step_code, participant_service,
                     required, compensatable, state, attempt_count, max_attempts,
                     last_dispatched_at, last_reply_at, applied_aggregate_version, applied_epoch,
                     failure_code, failure_message)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE
                    state = VALUES(state),
                    attempt_count = VALUES(attempt_count),
                    last_dispatched_at = VALUES(last_dispatched_at),
                    last_reply_at = VALUES(last_reply_at),
                    applied_aggregate_version = VALUES(applied_aggregate_version),
                    applied_epoch = VALUES(applied_epoch),
                    failure_code = VALUES(failure_code),
                    failure_message = VALUES(failure_message)
                """, steps, steps.size(), (java.sql.PreparedStatement ps, DeleteTreeSagaStep s) -> bindStep(ps, s));
    }

    @Override
    public List<DeleteTreeSagaStep> listSteps(UUID operationId) {
        return jdbc.query(
                "SELECT * FROM delete_tree_saga_step WHERE operation_id = ? ORDER BY sequence_no",
                STEP_MAPPER, operationId.toString());
    }

    @Override
    public void updateStep(DeleteTreeSagaStep s) { saveSteps(List.of(s)); }

    @Override
    public void saveCompensationSnapshot(UUID operationId, String participantService, String snapshotJson) {
        jdbc.update("""
                INSERT INTO delete_tree_compensation_snapshot
                    (operation_id, participant_service, snapshot_json, recorded_at)
                VALUES (?,?,?,?)
                ON DUPLICATE KEY UPDATE
                    snapshot_json = VALUES(snapshot_json),
                    recorded_at = VALUES(recorded_at)
                """, operationId.toString(), participantService, snapshotJson, Timestamp.from(Instant.now()));
    }

    @Override
    public Optional<String> loadCompensationSnapshot(UUID operationId, String participantService) {
        var rows = jdbc.query(
                "SELECT snapshot_json FROM delete_tree_compensation_snapshot " +
                        "WHERE operation_id = ? AND participant_service = ?",
                (rs, n) -> rs.getString(1),
                operationId.toString(), participantService);
        return rows.stream().findFirst();
    }

    @Override
    public List<DeleteTreeSagaState> listActivePastDeadline() {
        return jdbc.query(
                "SELECT * FROM delete_tree_saga_state " +
                        "WHERE state NOT IN ('SUCCEEDED','FAILED','MANUAL_REVIEW','CANCELLED') " +
                        "AND deadline_at < ?",
                STATE_MAPPER, Timestamp.from(Instant.now()));
    }

    private void bindState(java.sql.PreparedStatement ps, DeleteTreeSagaState s) throws SQLException {
        ps.setString(1, s.operationId().toString());
        ps.setString(2, s.treeId().toString());
        ps.setString(3, s.initiatingUserId().toString());
        ps.setString(4, s.correlationId().toString());
        ps.setString(5, s.state().name());
        ps.setLong(6, s.targetAggregateVersion());
        ps.setLong(7, s.targetEpoch());
        ps.setTimestamp(8, Timestamp.from(s.deadlineAt()));
        ps.setTimestamp(9, Timestamp.from(s.startedAt()));
        if (s.finalizedAt() != null) ps.setTimestamp(10, Timestamp.from(s.finalizedAt())); else ps.setNull(10, java.sql.Types.TIMESTAMP);
        ps.setTimestamp(11, Timestamp.from(s.lastUpdatedAt()));
        if (s.irreversibleAt() != null) ps.setTimestamp(12, Timestamp.from(s.irreversibleAt())); else ps.setNull(12, java.sql.Types.TIMESTAMP);
        ps.setString(13, s.failureCode());
        ps.setString(14, s.failureMessage());
    }

    private void bindStep(java.sql.PreparedStatement ps, DeleteTreeSagaStep s) throws SQLException {
        ps.setString(1, s.operationId().toString());
        ps.setInt(2, s.sequenceNo());
        ps.setString(3, s.stepCode());
        ps.setString(4, s.participantService());
        ps.setBoolean(5, s.required());
        ps.setBoolean(6, s.compensatable());
        ps.setString(7, s.state().name());
        ps.setInt(8, s.attemptCount());
        ps.setInt(9, s.maxAttempts());
        if (s.lastDispatchedAt() != null) ps.setTimestamp(10, Timestamp.from(s.lastDispatchedAt())); else ps.setNull(10, java.sql.Types.TIMESTAMP);
        if (s.lastReplyAt() != null)      ps.setTimestamp(11, Timestamp.from(s.lastReplyAt()));      else ps.setNull(11, java.sql.Types.TIMESTAMP);
        if (s.appliedAggregateVersion() != null) ps.setLong(12, s.appliedAggregateVersion());    else ps.setNull(12, java.sql.Types.BIGINT);
        if (s.appliedEpoch() != null)            ps.setLong(13, s.appliedEpoch());                else ps.setNull(13, java.sql.Types.BIGINT);
        ps.setString(14, s.failureCode());
        ps.setString(15, s.failureMessage());
    }

    private static final RowMapper<DeleteTreeSagaState> STATE_MAPPER = (ResultSet rs, int n) -> new DeleteTreeSagaState(
            UUID.fromString(rs.getString("operation_id")),
            UUID.fromString(rs.getString("tree_id")),
            UUID.fromString(rs.getString("initiating_user_id")),
            UUID.fromString(rs.getString("correlation_id")),
            DeleteTreeSagaState.State.valueOf(rs.getString("state")),
            rs.getLong("target_aggregate_version"),
            rs.getLong("target_epoch"),
            rs.getTimestamp("deadline_at").toInstant(),
            rs.getTimestamp("started_at").toInstant(),
            rs.getTimestamp("finalized_at") == null ? null : rs.getTimestamp("finalized_at").toInstant(),
            rs.getTimestamp("last_updated_at").toInstant(),
            rs.getTimestamp("irreversible_at") == null ? null : rs.getTimestamp("irreversible_at").toInstant(),
            rs.getString("failure_code"),
            rs.getString("failure_message")
    );

    private static final RowMapper<DeleteTreeSagaStep> STEP_MAPPER = (ResultSet rs, int n) -> new DeleteTreeSagaStep(
            UUID.fromString(rs.getString("operation_id")),
            rs.getInt("sequence_no"),
            rs.getString("step_code"),
            rs.getString("participant_service"),
            rs.getBoolean("required"),
            rs.getBoolean("compensatable"),
            DeleteTreeSagaStep.State.valueOf(rs.getString("state")),
            rs.getInt("attempt_count"),
            rs.getInt("max_attempts"),
            rs.getTimestamp("last_dispatched_at") == null ? null : rs.getTimestamp("last_dispatched_at").toInstant(),
            rs.getTimestamp("last_reply_at")      == null ? null : rs.getTimestamp("last_reply_at").toInstant(),
            (Long) rs.getObject("applied_aggregate_version"),
            (Long) rs.getObject("applied_epoch"),
            rs.getString("failure_code"),
            rs.getString("failure_message")
    );
}