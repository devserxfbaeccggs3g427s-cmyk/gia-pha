package com.familya.member.adapter.out.persistence;

import com.familya.member.application.port.out.DeleteMemberSagaRepository;
import com.familya.member.domain.model.DeleteMemberSagaState;
import com.familya.member.domain.model.DeleteMemberSagaStep;
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
public class JdbcDeleteMemberSagaRepository implements DeleteMemberSagaRepository {

    private final JdbcTemplate jdbc;

    public JdbcDeleteMemberSagaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void saveState(DeleteMemberSagaState s) {
        jdbc.update("""
                INSERT INTO delete_member_saga_state
                    (operation_id, tree_id, member_id, initiating_user_id, correlation_id,
                     state, target_aggregate_version, target_epoch,
                     deadline_at, started_at, finalized_at, last_updated_at, irreversible_at,
                     failure_code, failure_message)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE
                    state = VALUES(state),
                    finalized_at = VALUES(finalized_at),
                    last_updated_at = VALUES(last_updated_at),
                    irreversible_at = VALUES(irreversible_at),
                    failure_code = VALUES(failure_code),
                    failure_message = VALUES(failure_message)
                """,
                ps -> bindState(ps, s));
    }

    @Override
    public Optional<DeleteMemberSagaState> findState(UUID operationId) {
        List<DeleteMemberSagaState> rows = jdbc.query(
                "SELECT * FROM delete_member_saga_state WHERE operation_id = ?",
                STATE_MAPPER, operationId.toString());
        return rows.stream().findFirst();
    }

    @Override
    public void saveSteps(List<DeleteMemberSagaStep> steps) {
        if (steps.isEmpty()) return;
        jdbc.batchUpdate("""
                INSERT INTO delete_member_saga_step
                    (operation_id, sequence_no, step_code, participant_service,
                     required, compensatable, state, attempt_count, max_attempts,
                     next_attempt_at, last_dispatched_at, step_deadline_at,
                     dispatch_token, last_failure_at,
                     last_reply_at, applied_aggregate_version, applied_epoch,
                     failure_code, failure_message)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE
                    state = VALUES(state),
                    attempt_count = VALUES(attempt_count),
                    next_attempt_at = VALUES(next_attempt_at),
                    last_dispatched_at = VALUES(last_dispatched_at),
                    step_deadline_at = VALUES(step_deadline_at),
                    dispatch_token = VALUES(dispatch_token),
                    last_failure_at = VALUES(last_failure_at),
                    last_reply_at = VALUES(last_reply_at),
                    applied_aggregate_version = VALUES(applied_aggregate_version),
                    applied_epoch = VALUES(applied_epoch),
                    failure_code = VALUES(failure_code),
                    failure_message = VALUES(failure_message)
                """, steps, steps.size(), (java.sql.PreparedStatement ps, DeleteMemberSagaStep s) -> bindStep(ps, s));
    }

    @Override
    public List<DeleteMemberSagaStep> listSteps(UUID operationId) {
        return jdbc.query(
                "SELECT * FROM delete_member_saga_step WHERE operation_id = ? ORDER BY sequence_no",
                STEP_MAPPER, operationId.toString());
    }

    @Override
    public void updateStep(DeleteMemberSagaStep s) {
        saveSteps(List.of(s));
    }

    @Override
    public void saveCompensationSnapshot(UUID operationId, String participantService, String snapshotJson) {
        jdbc.update("""
                INSERT INTO delete_member_compensation_snapshot
                    (operation_id, participant_service, snapshot_json, recorded_at)
                VALUES (?,?,?,?)
                ON DUPLICATE KEY UPDATE
                    snapshot_json = VALUES(snapshot_json),
                    recorded_at = VALUES(recorded_at)
                """,
                operationId.toString(), participantService, snapshotJson, Timestamp.from(Instant.now()));
    }

    @Override
    public Optional<String> loadCompensationSnapshot(UUID operationId, String participantService) {
        List<String> rows = jdbc.query(
                "SELECT snapshot_json FROM delete_member_compensation_snapshot " +
                        "WHERE operation_id = ? AND participant_service = ?",
                (rs, n) -> rs.getString(1),
                operationId.toString(), participantService);
        return rows.stream().findFirst();
    }

    @Override
    public List<DeleteMemberSagaState> listDispatchedPastDeadline() {
        return jdbc.query(
                "SELECT * FROM delete_member_saga_state " +
                        "WHERE state IN ('PENDING','DISPATCHED','COMPENSATING') " +
                        "AND deadline_at < ?",
                STATE_MAPPER, Timestamp.from(Instant.now()));
    }

    @Override
    public List<DeleteMemberSagaStep> listRetryableSteps(Instant now, int limit) {
        return jdbc.query(
                "SELECT * FROM delete_member_saga_step " +
                        "WHERE state = 'FAILED' " +
                        "AND attempt_count < max_attempts " +
                        "AND next_attempt_at IS NOT NULL " +
                        "AND next_attempt_at <= ? " +
                        "ORDER BY next_attempt_at " +
                        "LIMIT ?",
                STEP_MAPPER, Timestamp.from(now), limit);
    }

    @Override
    public List<DeleteMemberSagaStep> listTimedOutSteps(Instant now, int limit) {
        return jdbc.query(
                "SELECT * FROM delete_member_saga_step " +
                        "WHERE state = 'DISPATCHED' " +
                        "AND dispatch_token IS NOT NULL " +
                        "AND step_deadline_at IS NOT NULL " +
                        "AND step_deadline_at <= ? " +
                        "ORDER BY step_deadline_at " +
                        "LIMIT ?",
                STEP_MAPPER, Timestamp.from(now), limit);
    }

    @Override
    public boolean tryClaimDispatch(UUID operationId, int sequenceNo, UUID dispatchToken,
                                    Instant now, Instant stepDeadlineAt) {
        int updated = jdbc.update("""
                UPDATE delete_member_saga_step
                   SET state = 'DISPATCHED',
                       dispatch_token = ?,
                       last_dispatched_at = ?,
                       step_deadline_at = ?,
                       next_attempt_at = NULL
                 WHERE operation_id = ?
                   AND sequence_no = ?
                   AND dispatch_token IS NULL
                   AND state IN ('PENDING','FAILED')
                """,
                dispatchToken.toString(),
                Timestamp.from(now),
                Timestamp.from(stepDeadlineAt),
                operationId.toString(),
                sequenceNo);
        return updated == 1;
    }

    @Override
    public boolean releaseOrScheduleRetry(UUID operationId, int sequenceNo, Instant now,
                                          Instant nextAttemptAt, String failureCode, String failureMessage) {
        int updated = jdbc.update("""
                UPDATE delete_member_saga_step
                   SET state = 'FAILED',
                       dispatch_token = NULL,
                       step_deadline_at = NULL,
                       next_attempt_at = ?,
                       last_failure_at = ?,
                       failure_code = ?,
                       failure_message = ?
                 WHERE operation_id = ?
                   AND sequence_no = ?
                   AND state = 'DISPATCHED'
                   AND dispatch_token IS NOT NULL
                """,
                Timestamp.from(nextAttemptAt),
                Timestamp.from(now),
                failureCode,
                failureMessage,
                operationId.toString(),
                sequenceNo);
        return updated == 1;
    }

    @Override
    public boolean tryClaimCompensation(UUID operationId, int sequenceNo, UUID dispatchToken,
                                        Instant now, Instant stepDeadlineAt) {
        int updated = jdbc.update("""
                UPDATE delete_member_saga_step
                   SET state = 'DISPATCHED',
                       dispatch_token = ?,
                       last_dispatched_at = ?,
                       step_deadline_at = ?,
                       next_attempt_at = NULL
                 WHERE operation_id = ?
                   AND sequence_no = ?
                   AND state = 'ACK'
                   AND dispatch_token IS NULL
                """,
                dispatchToken.toString(),
                Timestamp.from(now),
                Timestamp.from(stepDeadlineAt),
                operationId.toString(),
                sequenceNo);
        return updated == 1;
    }

    @Override
    public boolean tryAcknowledgeStep(UUID operationId, int sequenceNo, Instant now,
                                      long appliedAggregateVersion, long appliedEpoch) {
        int updated = jdbc.update("""
                UPDATE delete_member_saga_step
                   SET state = 'ACK',
                       last_reply_at = ?,
                       applied_aggregate_version = ?,
                       applied_epoch = ?,
                       step_deadline_at = NULL
                 WHERE operation_id = ?
                   AND sequence_no = ?
                   AND state = 'DISPATCHED'
                   AND dispatch_token IS NOT NULL
                """,
                Timestamp.from(now),
                appliedAggregateVersion,
                appliedEpoch,
                operationId.toString(),
                sequenceNo);
        return updated == 1;
    }

    @Override
    public Optional<DeleteMemberSagaStep> findActiveStep(UUID operationId) {
        var rows = jdbc.query("""
                SELECT s.* FROM delete_member_saga_step s
                WHERE s.operation_id = ?
                  AND s.state NOT IN ('ACK','COMPENSATED','DEAD_LETTERED')
                ORDER BY s.sequence_no
                LIMIT 1
                """, STEP_MAPPER, operationId.toString());
        return rows.stream().findFirst();
    }

    @Override
    public void markOperationManualReview(UUID operationId, String failureCode, String failureMessage, Instant now) {
        jdbc.update("""
                UPDATE delete_member_saga_state
                   SET state = 'MANUAL_REVIEW',
                       failure_code = ?,
                       failure_message = ?,
                       finalized_at = ?,
                       last_updated_at = ?
                 WHERE operation_id = ?
                   AND state <> 'MANUAL_REVIEW'
                """,
                failureCode,
                failureMessage,
                Timestamp.from(now),
                Timestamp.from(now),
                operationId.toString());
    }

    private void bindState(java.sql.PreparedStatement ps, DeleteMemberSagaState s) throws SQLException {
        ps.setString(1, s.operationId().toString());
        ps.setString(2, s.treeId().toString());
        ps.setString(3, s.memberId().toString());
        ps.setString(4, s.initiatingUserId().toString());
        ps.setString(5, s.correlationId().toString());
        ps.setString(6, s.state().name());
        ps.setLong(7, s.targetAggregateVersion());
        ps.setLong(8, s.targetEpoch());
        ps.setTimestamp(9, Timestamp.from(s.deadlineAt()));
        ps.setTimestamp(10, Timestamp.from(s.startedAt()));
        if (s.finalizedAt() != null) ps.setTimestamp(11, Timestamp.from(s.finalizedAt())); else ps.setNull(11, java.sql.Types.TIMESTAMP);
        ps.setTimestamp(12, Timestamp.from(s.lastUpdatedAt()));
        if (s.irreversibleAt() != null) ps.setTimestamp(13, Timestamp.from(s.irreversibleAt())); else ps.setNull(13, java.sql.Types.TIMESTAMP);
        ps.setString(14, s.failureCode());
        ps.setString(15, s.failureMessage());
    }

    private void bindStep(java.sql.PreparedStatement ps, DeleteMemberSagaStep s) throws SQLException {
        ps.setString(1, s.operationId().toString());
        ps.setInt(2, s.sequenceNo());
        ps.setString(3, s.stepCode());
        ps.setString(4, s.participantService());
        ps.setBoolean(5, s.required());
        ps.setBoolean(6, s.compensatable());
        ps.setString(7, s.state().name());
        ps.setInt(8, s.attemptCount());
        ps.setInt(9, s.maxAttempts());
        if (s.nextAttemptAt() != null) ps.setTimestamp(10, Timestamp.from(s.nextAttemptAt())); else ps.setNull(10, java.sql.Types.TIMESTAMP);
        if (s.lastDispatchedAt() != null) ps.setTimestamp(11, Timestamp.from(s.lastDispatchedAt())); else ps.setNull(11, java.sql.Types.TIMESTAMP);
        if (s.stepDeadlineAt() != null) ps.setTimestamp(12, Timestamp.from(s.stepDeadlineAt())); else ps.setNull(12, java.sql.Types.TIMESTAMP);
        if (s.dispatchToken() != null) ps.setString(13, s.dispatchToken().toString()); else ps.setNull(13, java.sql.Types.CHAR);
        if (s.lastFailureAt() != null) ps.setTimestamp(14, Timestamp.from(s.lastFailureAt())); else ps.setNull(14, java.sql.Types.TIMESTAMP);
        if (s.lastReplyAt() != null) ps.setTimestamp(15, Timestamp.from(s.lastReplyAt())); else ps.setNull(15, java.sql.Types.TIMESTAMP);
        if (s.appliedAggregateVersion() != null) ps.setLong(16, s.appliedAggregateVersion()); else ps.setNull(16, java.sql.Types.BIGINT);
        if (s.appliedEpoch() != null) ps.setLong(17, s.appliedEpoch()); else ps.setNull(17, java.sql.Types.BIGINT);
        ps.setString(18, s.failureCode());
        ps.setString(19, s.failureMessage());
    }

    private static final RowMapper<DeleteMemberSagaState> STATE_MAPPER = (ResultSet rs, int n) -> new DeleteMemberSagaState(
            UUID.fromString(rs.getString("operation_id")),
            UUID.fromString(rs.getString("tree_id")),
            UUID.fromString(rs.getString("member_id")),
            UUID.fromString(rs.getString("initiating_user_id")),
            UUID.fromString(rs.getString("correlation_id")),
            DeleteMemberSagaState.State.valueOf(rs.getString("state")),
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

    private static final RowMapper<DeleteMemberSagaStep> STEP_MAPPER = (ResultSet rs, int n) -> new DeleteMemberSagaStep(
            UUID.fromString(rs.getString("operation_id")),
            rs.getInt("sequence_no"),
            rs.getString("step_code"),
            rs.getString("participant_service"),
            rs.getBoolean("required"),
            rs.getBoolean("compensatable"),
            DeleteMemberSagaStep.State.valueOf(rs.getString("state")),
            rs.getInt("attempt_count"),
            rs.getInt("max_attempts"),
            rs.getTimestamp("next_attempt_at") == null ? null : rs.getTimestamp("next_attempt_at").toInstant(),
            rs.getTimestamp("last_dispatched_at") == null ? null : rs.getTimestamp("last_dispatched_at").toInstant(),
            rs.getTimestamp("step_deadline_at") == null ? null : rs.getTimestamp("step_deadline_at").toInstant(),
            rs.getString("dispatch_token") == null ? null : UUID.fromString(rs.getString("dispatch_token")),
            rs.getTimestamp("last_failure_at") == null ? null : rs.getTimestamp("last_failure_at").toInstant(),
            rs.getTimestamp("last_reply_at") == null ? null : rs.getTimestamp("last_reply_at").toInstant(),
            (Long) rs.getObject("applied_aggregate_version"),
            (Long) rs.getObject("applied_epoch"),
            rs.getString("failure_code"),
            rs.getString("failure_message")
    );
}
