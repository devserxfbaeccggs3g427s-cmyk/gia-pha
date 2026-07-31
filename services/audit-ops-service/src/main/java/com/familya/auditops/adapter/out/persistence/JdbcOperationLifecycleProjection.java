/**
 * Adapter JDBC cho {@link com.familya.auditops.application.port.out.OperationLifecycleProjection}.
 *
 * <p>Đây là projection công khai (read-side) của vòng đời operation. Mọi ghi đều
 * chạy trong transaction hiện hành của consumer ({@code @Transactional} trên
 * listener) để inbox + projection + watermark cùng commit hoặc cùng rollback.</p>
 *
 * <p>Read views trả về dữ liệu phục vụ operator UI: operation theo trạng thái,
 * operation theo owner, operation quá hạn (stale), watermark per topic, count
 * theo trạng thái / owner / state. Không có read view nào sửa đổi dữ liệu
 * và không có read view nào dùng làm business authority.</p>
 */
package com.familya.auditops.adapter.out.persistence;

import com.familya.auditops.application.port.out.OperationLifecycleProjection;
import com.familya.auditops.domain.model.OperationLifecycleRow;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcOperationLifecycleProjection implements OperationLifecycleProjection {

    private static final String UPSERT_STARTED = """
            INSERT INTO operation_lifecycle_projection
                (operation_id, owner_service, saga_type, tree_id, initiating_user_id,
                 state, target_version, target_epoch, failure_code, failure_message, failure_routing,
                 started_at, updated_at, finalized_at, last_event_id)
            VALUES (:op,:owner,:saga,:tree,:user,:state,:tv,:te,:fc,:fm,:fr,:sa,:ua,:fa,:eid)
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
            """;

    private static final String UPSERT_STATE = """
            INSERT INTO operation_lifecycle_projection
                (operation_id, owner_service, saga_type, tree_id, initiating_user_id,
                 state, target_version, target_epoch, failure_code, failure_message, failure_routing,
                 started_at, updated_at, finalized_at, last_event_id)
            VALUES (:op,:owner,:saga,:tree,:user,:state,:tv,:te,:fc,:fm,:fr,:sa,:ua,:fa,:eid)
            ON DUPLICATE KEY UPDATE
                state = VALUES(state),
                failure_code = VALUES(failure_code),
                failure_message = VALUES(failure_message),
                failure_routing = VALUES(failure_routing),
                updated_at = VALUES(updated_at),
                finalized_at = COALESCE(VALUES(finalized_at), finalized_at),
                last_event_id = VALUES(last_event_id)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcOperationLifecycleProjection(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void upsertStarted(OperationLifecycleRow row, String lastEventId) {
        jdbc.update(UPSERT_STARTED, params(row, lastEventId, row.finalizedAt()));
    }

    @Override
    public void applyStateChange(OperationLifecycleRow row, Instant finalizedAt, String lastEventId) {
        jdbc.update(UPSERT_STATE, params(row, lastEventId, finalizedAt));
    }

    private static MapSqlParameterSource params(OperationLifecycleRow row, String lastEventId, Instant finalizedAt) {
        return new MapSqlParameterSource()
                .addValue("op", row.operationId().toString())
                .addValue("owner", row.ownerService())
                .addValue("saga", row.sagaType())
                .addValue("tree", row.treeId() == null ? null : row.treeId().toString())
                .addValue("user", row.initiatingUserId() == null ? null : row.initiatingUserId().toString())
                .addValue("state", row.state())
                .addValue("tv", row.targetVersion())
                .addValue("te", row.targetEpoch())
                .addValue("fc", row.failureCode())
                .addValue("fm", row.failureMessage())
                .addValue("fr", row.failureRouting())
                .addValue("sa", Timestamp.from(row.startedAt()))
                .addValue("ua", Timestamp.from(row.updatedAt()))
                .addValue("fa", finalizedAt == null ? null : Timestamp.from(finalizedAt))
                .addValue("eid", lastEventId);
    }

    @Override
    public Optional<OperationLifecycleRow> find(UUID operationId) {
        var rows = jdbc.query("SELECT * FROM operation_lifecycle_projection WHERE operation_id = :op",
                new MapSqlParameterSource("op", operationId.toString()), MAPPER);
        return rows.stream().findFirst();
    }

    @Override
    public List<OperationLifecycleRow> findByState(String state, int limit) {
        return jdbc.query(
                "SELECT * FROM operation_lifecycle_projection WHERE state = :state ORDER BY updated_at DESC LIMIT :lim",
                new MapSqlParameterSource().addValue("state", state).addValue("lim", limit),
                MAPPER);
    }

    @Override
    public List<OperationLifecycleRow> findByOwnerService(String ownerService, int limit) {
        return jdbc.query(
                "SELECT * FROM operation_lifecycle_projection WHERE owner_service = :owner ORDER BY updated_at DESC LIMIT :lim",
                new MapSqlParameterSource().addValue("owner", ownerService).addValue("lim", limit),
                MAPPER);
    }

    @Override
    public long countByState(String state) {
        Long c = jdbc.queryForObject(
                "SELECT COUNT(1) FROM operation_lifecycle_projection WHERE state = :state",
                new MapSqlParameterSource("state", state), Long.class);
        return c == null ? 0L : c;
    }

    @Override
    public long countByOwnerServiceAndState(String ownerService, String state) {
        Long c = jdbc.queryForObject(
                "SELECT COUNT(1) FROM operation_lifecycle_projection WHERE owner_service = :owner AND state = :state",
                new MapSqlParameterSource().addValue("owner", ownerService).addValue("state", state),
                Long.class);
        return c == null ? 0L : c;
    }

    @Override
    public List<OperationLifecycleRow> findStale(Instant olderThan, int limit) {
        return jdbc.query(
                "SELECT * FROM operation_lifecycle_projection "
                        + "WHERE state NOT IN ('SUCCEEDED','FAILED','COMPENSATED','MANUAL_REVIEW') "
                        + "AND updated_at < :older ORDER BY updated_at ASC LIMIT :lim",
                new MapSqlParameterSource()
                        .addValue("older", Timestamp.from(olderThan))
                        .addValue("lim", limit),
                MAPPER);
    }

    @Override
    public List<Watermark> listWatermarks() {
        return jdbc.query(
                "SELECT topic, last_offset, last_seen_at FROM projection_watermark ORDER BY topic",
                (rs, n) -> new Watermark(rs.getString("topic"),
                        rs.getLong("last_offset"),
                        rs.getTimestamp("last_seen_at").toInstant()));
    }

    public void recordWatermark(String topic, String consumerGroup, String eventId,
                                long offset, int partition, Instant when) {
        jdbc.update("""
                INSERT INTO projection_watermark
                    (topic, consumer_group, last_event_id, last_offset, last_partition, last_seen_at, record_count)
                VALUES (:topic,:group,:eid,:off,:part,:seen,1)
                ON DUPLICATE KEY UPDATE
                    last_event_id = VALUES(last_event_id),
                    last_offset = VALUES(last_offset),
                    last_partition = VALUES(last_partition),
                    last_seen_at = VALUES(last_seen_at),
                    record_count = record_count + 1
                """,
                new MapSqlParameterSource()
                        .addValue("topic", topic)
                        .addValue("group", consumerGroup)
                        .addValue("eid", eventId)
                        .addValue("off", offset)
                        .addValue("part", partition)
                        .addValue("seen", Timestamp.from(when)));
    }

    public void recordOffset(String topic, int partition, long offset, Instant when) {
        jdbc.update("""
                INSERT INTO projection_offset_ledger
                    (topic, partition_no, last_offset, last_seen_at)
                VALUES (:topic,:part,:off,:seen)
                ON DUPLICATE KEY UPDATE
                    last_offset = GREATEST(last_offset, VALUES(last_offset)),
                    last_seen_at = VALUES(last_seen_at)
                """,
                new MapSqlParameterSource()
                        .addValue("topic", topic)
                        .addValue("part", partition)
                        .addValue("off", offset)
                        .addValue("seen", Timestamp.from(when)));
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
