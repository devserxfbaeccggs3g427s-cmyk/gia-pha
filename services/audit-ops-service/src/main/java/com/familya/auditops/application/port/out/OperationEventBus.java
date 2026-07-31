/**
 * Port publish {@link com.familya.auditops.domain.event.AuditOpsEvent}
 * lên outbox cục bộ trong transaction hiện tại.
 *
 * <p>Các triển khai được ràng buộc bằng {@code Propagation.MANDATORY}
 * để nếu thiếu transaction thì lỗi sẽ nổ ra lúc khởi động thay vì
 * âm thầm đánh rơi message.</p>
 */
package com.familya.auditops.application.port.out;

import com.familya.auditops.domain.event.AuditOpsEvent;

import java.util.Map;

/**
 * Interface publish sự kiện operation thông qua outbox.
 *
 * <p>Triển khai mặc định là {@code OutboxOperationEventPublisher}.</p>
 */
public interface OperationEventBus {

    /**
     * Stage một {@link AuditOpsEvent} vào outbox.
     *
     * @param event        sự kiện cần publish
     * @param headers      header bổ sung (correlation, causation, ...)
     */
    void publish(AuditOpsEvent event, Map<String, String> headers);
}