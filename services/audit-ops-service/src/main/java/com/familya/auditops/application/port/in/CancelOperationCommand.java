/**
 * Command huỷ một operation chưa kết thúc.
 *
 * <p>Đây là hành động của operator: việc phát lệnh này sẽ chuyển
 * operation sang trạng thái {@code MANUAL_REVIEW} để việc huỷ được
 * phản ánh trong audit log; các hành động tiếp theo của operator sẽ
 * quyết định cách dọn dẹp thực sự.</p>
 */
package com.familya.auditops.application.port.in;

import java.util.Objects;
import java.util.UUID;

/**
 * Command đóng gói thông tin cần thiết để huỷ một operation.
 *
 * <p>Tất cả các trường đều bắt buộc; constructor sử dụng
 * {@link Objects#requireNonNull} để đảm bảo rằng caller cung cấp đủ
 * thông tin.</p>
 */
public final class CancelOperationCommand {

    /** UUID của operator thực hiện huỷ. */
    private final UUID operatorUserId;
    /** UUID của operation cần huỷ. */
    private final UUID operationId;
    /** Lý do huỷ (bắt buộc, sẽ được ghi vào audit log). */
    private final String reason;

    /**
     * Khởi tạo command.
     *
     * @param operatorUserId UUID của operator
     * @param operationId    UUID của operation
     * @param reason         lý do huỷ
     * @throws NullPointerException nếu bất kỳ tham số nào null
     */
    public CancelOperationCommand(UUID operatorUserId, UUID operationId, String reason) {
        this.operatorUserId = Objects.requireNonNull(operatorUserId);
        this.operationId = Objects.requireNonNull(operationId);
        this.reason = Objects.requireNonNull(reason);
    }

    /** @return UUID của operator. */
    public UUID operatorUserId() { return operatorUserId; }
    /** @return UUID của operation. */
    public UUID operationId() { return operationId; }
    /** @return lý do huỷ. */
    public String reason() { return reason; }
}