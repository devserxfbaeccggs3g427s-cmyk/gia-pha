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

/**
 * Triển khai JDBC của {@link DeleteMemberSagaRepository}. Cung cấp các thao tác đọc/ghi
 * cho state Saga xóa thành viên, các bước Saga, snapshot bù trừ (compensation snapshot)
 * cùng các truy vấn phục vụ deadline scanner.
 *
 * <p>Bean {@code @Repository} thuộc tầng adapter-out/persistence trong kiến trúc Hexagonal.
 * Các phương thức {@code tryClaim*} sử dụng cập nhật có điều kiện để đảm bảo chỉ một
 * dispatcher có thể giành quyền gửi một bước Saga tại một thời điểm (qua {@code dispatch_token}).
 */
@Repository
public class JdbcDeleteMemberSagaRepository implements DeleteMemberSagaRepository {

    private final JdbcTemplate jdbc;

    /**
     * Khởi tạo repository với {@link JdbcTemplate}.
     *
     * @param jdbc template JDBC dùng cho mọi truy vấn
     */
    public JdbcDeleteMemberSagaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Lưu (insert hoặc update) trạng thái Saga. Sử dụng {@code ON DUPLICATE KEY UPDATE}
     * để upsert theo {@code operation_id}.
     *
     * @param s trạng thái Saga cần lưu
     */
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

    /**
     * Tra cứu trạng thái Saga theo {@code operationId}.
     *
     * @param operationId mã operationId của Saga
     * @return trạng thái Saga hoặc {@link Optional#empty()}
     */
    @Override
    public Optional<DeleteMemberSagaState> findState(UUID operationId) {
        List<DeleteMemberSagaState> rows = jdbc.query(
                "SELECT * FROM delete_member_saga_state WHERE operation_id = ?",
                STATE_MAPPER, operationId.toString());
        return rows.stream().findFirst();
    }

    /**
     * Lưu nhiều bước Saga trong một batch (insert hoặc update). Nếu danh sách rỗng thì không làm gì.
     *
     * @param steps danh sách các bước cần lưu
     */
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

    /**
     * Liệt kê tất cả các bước của một Saga, sắp xếp theo {@code sequence_no} tăng dần.
     *
     * @param operationId mã operationId của Saga
     * @return danh sách các bước
     */
    @Override
    public List<DeleteMemberSagaStep> listSteps(UUID operationId) {
        return jdbc.query(
                "SELECT * FROM delete_member_saga_step WHERE operation_id = ? ORDER BY sequence_no",
                STEP_MAPPER, operationId.toString());
    }

    /**
     * Cập nhật một bước Saga (thực chất là gọi {@link #saveSteps} với danh sách một phần tử).
     *
     * @param s bước Saga cần cập nhật
     */
    @Override
    public void updateStep(DeleteMemberSagaStep s) {
        saveSteps(List.of(s));
    }

    /**
     * Lưu snapshot bù trừ cho một participant. Snapshot cho phép khôi phục trạng thái
     * trước khi bước forward thay đổi nó.
     *
     * @param operationId       mã operationId của Saga
     * @param participantService dịch vụ tham gia
     * @param snapshotJson      payload snapshot ở dạng JSON
     */
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

    /**
     * Tải snapshot bù trừ đã lưu trước đó cho một participant.
     *
     * @param operationId       mã operationId của Saga
     * @param participantService dịch vụ tham gia
     * @return payload snapshot dạng JSON hoặc {@link Optional#empty()}
     */
    @Override
    public Optional<String> loadCompensationSnapshot(UUID operationId, String participantService) {
        List<String> rows = jdbc.query(
                "SELECT snapshot_json FROM delete_member_compensation_snapshot " +
                        "WHERE operation_id = ? AND participant_service = ?",
                (rs, n) -> rs.getString(1),
                operationId.toString(), participantService);
        return rows.stream().findFirst();
    }

    /**
     * Liệt kê các Saga ở trạng thái không cuối (PENDING/DISPATCHED/COMPENSATING) đã vượt quá
     * deadline. Dùng bởi deadline scanner để phát hiện các operation cần xử lý.
     *
     * @return danh sách các trạng thái Saga quá hạn
     */
    @Override
    public List<DeleteMemberSagaState> listDispatchedPastDeadline() {
        return jdbc.query(
                "SELECT * FROM delete_member_saga_state " +
                        "WHERE state IN ('PENDING','DISPATCHED','COMPENSATING') " +
                        "AND deadline_at < ?",
                STATE_MAPPER, Timestamp.from(Instant.now()));
    }

    /**
     * Liệt kê các bước Saga đang FAILED nhưng có thể thử lại (còn lượt và đã tới thời điểm retry).
     *
     * @param now   thời điểm hiện tại dùng để so sánh
     * @param limit số lượng tối đa bản ghi trả về
     * @return danh sách các bước có thể thử lại, sắp xếp theo thời điểm retry
     */
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

    /**
     * Liệt kê các bước Saga ở trạng thái DISPATCHED đã vượt quá deadline riêng của bước.
     *
     * @param now   thời điểm hiện tại
     * @param limit giới hạn số bản ghi
     * @return danh sách các bước bị timeout
     */
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

    /**
     * Cố gắng giành quyền dispatch cho một bước Saga. Sử dụng cập nhật có điều kiện dựa trên
     * {@code dispatch_token IS NULL} để đảm bảo chỉ một dispatcher thành công.
     *
     * @param operationId    mã operationId
     * @param sequenceNo     số thứ tự bước
     * @param dispatchToken  token duy nhất cho lần dispatch này
     * @param now            thời điểm dispatch
     * @param stepDeadlineAt thời hạn bước
     * @return {@code true} nếu giành được quyền, {@code false} nếu bước đã được dispatch
     */
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

    /**
     * Giải phóng token dispatch và lên lịch thử lại cho một bước khi bước đó thất bại.
     *
     * @param operationId    mã operationId
     * @param sequenceNo     số thứ tự bước
     * @param now            thời điểm hiện tại
     * @param nextAttemptAt  thời điểm dự kiến thử lại
     * @param failureCode    mã lỗi
     * @param failureMessage mô tả lỗi
     * @return {@code true} nếu cập nhật thành công
     */
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

    /**
     * Giành quyền dispatch cho compensation: chỉ các bước đang ở trạng thái ACK và chưa có
     * dispatch token mới có thể được bù trừ.
     *
     * @param operationId    mã operationId
     * @param sequenceNo     số thứ tự bước
     * @param dispatchToken  token duy nhất cho lần compensation này
     * @param now            thời điểm hiện tại
     * @param stepDeadlineAt thời hạn compensation
     * @return {@code true} nếu giành được quyền
     */
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

    /**
     * Đánh dấu một bước Saga là ACK. Chỉ áp dụng khi bước đang DISPATCHED và đã có dispatch token.
     *
     * @param operationId           mã operationId
     * @param sequenceNo            số thứ tự bước
     * @param now                   thời điểm ACK
     * @param appliedAggregateVersion phiên bản aggregate đã áp dụng
     * @param appliedEpoch          epoch đã áp dụng
     * @return {@code true} nếu cập nhật thành công
     */
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

    /**
     * Tìm bước đang hoạt động (chưa ACK, COMPENSATED hoặc DEAD_LETTERED) đầu tiên theo sequence.
     *
     * @param operationId mã operationId
     * @return bước đang hoạt động hoặc {@link Optional#empty()}
     */
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

    /**
     * Đánh dấu operation ở trạng thái MANUAL_REVIEW (cần con người can thiệp).
     *
     * @param operationId    mã operationId
     * @param failureCode    mã lỗi tổng quát
     * @param failureMessage mô tả lỗi
     * @param now            thời điểm đánh dấu
     */
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

    /**
     * Bind tham số cho câu INSERT trạng thái Saga. Xử lý các giá trị null đúng kiểu SQL.
     *
     * @param ps prepared statement đã được chuẩn bị
     * @param s  trạng thái Saga cần bind
     * @throws SQLException nếu có lỗi khi set tham số
     */
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

    /**
     * Bind tham số cho câu INSERT bước Saga.
     *
     * @param ps prepared statement
     * @param s  bước Saga cần bind
     * @throws SQLException nếu có lỗi khi set tham số
     */
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
