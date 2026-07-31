/**
 * Lớp cơ sở cho các sự kiện mà audit-ops publish ngược lại cho platform.
 *
 * <p>Tất cả các sự kiện đều được phân vùng theo {@code operationId}
 * để giữ thứ tự chuỗi sự kiện cho từng operation khi tiêu thụ.</p>
 */
package com.familya.auditops.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Lớp abstract định nghĩa các thuộc tính chung cho mọi sự kiện
 * audit-ops: operationId, eventType, eventVersion, occurredAt.
 *
 * <p>Cung cấp mặc định cho {@link #topic()} và {@link #partitionKey()}.</p>
 */
public abstract class AuditOpsEvent {

    /**
     * @return UUID của operation liên quan tới sự kiện
     */
    public abstract UUID operationId();

    /**
     * @return tên loại sự kiện (ví dụ: {@code "auditops.operation.advanced"})
     */
    public abstract String eventType();

    /**
     * @return phiên bản schema của sự kiện
     */
    public abstract int eventVersion();

    /**
     * @return thời điểm sự kiện xảy ra
     */
    public abstract Instant occurredAt();

    /**
     * @return tên topic Kafka mặc định cho sự kiện
     */
    public String topic() { return "auditops.events.v1"; }

    /**
     * @return khoá phân vùng; mặc định là operationId để đảm bảo thứ tự
     */
    public String partitionKey() { return operationId().toString(); }
}