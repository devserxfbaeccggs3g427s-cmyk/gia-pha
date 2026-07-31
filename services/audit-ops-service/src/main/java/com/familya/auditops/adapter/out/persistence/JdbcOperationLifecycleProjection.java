/**
 * Adapter JDBC cho port {@link com.familya.auditops.application.port.out.OperationLifecycleProjection}.
 *
 * <p>Bảng {@code operation_lifecycle_projection} là nơi lưu trữ
 * projection vòng đời operation cho operator UI và replay. Mỗi row
 * tương ứng với một operation; việc cập nhật dùng {@code INSERT ...
 * ON DUPLICATE KEY UPDATE} để vừa chèn vừa cập nhật một cách nguyên tử.</p>
 */
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

/**
 * Triển khai {@link OperationLifecycleProjection} bằng JdbcTemplate.
 *
 * <p>Cấu trúc bảng:</p>
 * <ul>
 *   <li>{@code operation_id} (UUID, primary key).</li>
 *   <li>Các cột mô tả service sở hữu, loại Saga, trạng thái, version,
 *       mã lỗi, routing.</li>
 *   <li>Các cột thời gian: {@code started_at}, {@code updated_at}, {@code finalized_at}.</li>
 *   <li>{@code last_event_id} để hỗ trợ debug và replay.</li>
 * </ul>
 */
@Repository
public class JdbcOperationLifecycleProjection implements OperationLifecycleProjection {

    /** JDBC template. */
    private final JdbcTemplate jdbc;

    /**
     * Khởi tạo projection.
     *
     * @param jdbc JDBC template
     */
    public JdbcOperationLifecycleProjection(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Chèn hoặc cập nhật row khi nhận sự kiện {@code OperationStarted}.
     *
     * <p>Khi row đã tồn tại, chỉ ghi đè các trường phù hợp với sự kiện
     * bắt đầu. {@code finalized_at} được bảo toàn bằng {@code COALESCE}
     * để không đè lên dấu thời gian kết thúc thực tế.</p>
     *
     * @param row         dữ liệu vòng đời mới
     * @param lastEventId id của sự kiện Kafka gốc
     */
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

    /**
     * Cập nhật row khi nhận sự kiện thay đổi trạng thái.
     *
     * <p>Khác với {@link #upsertStarted}, phương thức này không ghi đè
     * các trường "started" để bảo toàn thời điểm bắt đầu. Nếu
     * {@code finalizedAt} được cung cấp và row chưa có finalized_at,
     * sẽ được set.</p>
     *
     * @param row         dữ liệu vòng đời mới
     * @param finalizedAt thời điểm kết thúc (hoặc null)
     * @param lastEventId id sự kiện
     */
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

    /**
     * Tìm projection của một operation theo id.
     *
     * @param operationId id operation
     * @return {@link Optional} chứa {@link OperationLifecycleRow} nếu tồn tại
     */
    @Override
    public Optional<OperationLifecycleRow> find(UUID operationId) {
        var rows = jdbc.query("SELECT * FROM operation_lifecycle_projection WHERE operation_id = ?",
                MAPPER, operationId.toString());
        return rows.stream().findFirst();
    }

    /** {@link RowMapper} dùng chung cho {@link OperationLifecycleRow}. */
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