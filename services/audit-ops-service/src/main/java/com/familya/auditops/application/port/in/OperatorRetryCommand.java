/**
 * Command retry một Saga kết thúc ở {@code MANUAL_REVIEW} hoặc đã
 * thất bại compensation.
 *
 * <p>Orchestrator sẽ:</p>
 * <ul>
 *   <li>Tăng bộ đếm attempt của các step chưa ack.</li>
 *   <li>Phát lại lệnh cho các step chưa hoàn thành.</li>
 *   <li>Chuyển operation sang {@code RUNNING}.</li>
 * </ul>
 *
 * <p>Hành động này được ghi vào audit log.</p>
 */
package com.familya.auditops.application.port.in;

import java.util.Objects;
import java.util.UUID;

/**
 * Command đóng gói thông tin để retry một Saga.
 *
 * <p>Tất cả các trường đều bắt buộc; constructor sử dụng
 * {@link Objects#requireNonNull} để đảm bảo rằng caller cung cấp đủ
 * thông tin.</p>
 */
public final class OperatorRetryCommand {

    /** UUID của operator thực hiện retry. */
    private final UUID operatorUserId;
    /** UUID của operation cần retry. */
    private final UUID operationId;
    /** Lý do retry (sẽ được ghi vào audit log). */
    private final String reason;

    /**
     * Khởi tạo command.
     *
     * @param operatorUserId UUID của operator
     * @param operationId    UUID của operation
     * @param reason         lý do retry
     * @throws NullPointerException nếu bất kỳ tham số nào null
     */
    public OperatorRetryCommand(UUID operatorUserId, UUID operationId, String reason) {
        this.operatorUserId = Objects.requireNonNull(operatorUserId);
        this.operationId = Objects.requireNonNull(operationId);
        this.reason = Objects.requireNonNull(reason);
    }

    /** @return UUID của operator. */
    public UUID operatorUserId() { return operatorUserId; }
    /** @return UUID của operation. */
    public UUID operationId() { return operationId; }
    /** @return lý do retry. */
    public String reason() { return reason; }
}