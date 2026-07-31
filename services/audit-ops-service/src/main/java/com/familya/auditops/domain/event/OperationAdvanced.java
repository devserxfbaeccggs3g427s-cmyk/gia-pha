/**
 * Sự kiện phát ra khi operation chuyển trạng thái.
 *
 * <p>Loại sự kiện: {@code "auditops.operation.advanced"}.</p>
 */
package com.familya.auditops.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Sự kiện cụ thể ghi nhận việc operation chuyển từ {@code fromStatus}
 * sang {@code toStatus}. Được publish qua {@link com.familya.auditops.application.port.out.OperationEventBus}.
 */
public final class OperationAdvanced extends AuditOpsEvent {

    /** UUID của operation. */
    private final UUID operationId;
    /** Trạng thái trước. */
    private final String fromStatus;
    /** Trạng thái sau. */
    private final String toStatus;
    /** Tác nhân gây ra chuyển trạng thái (operator, service, ...). */
    private final String actor;
    /** Thời điểm sự kiện. */
    private final Instant occurredAt;
    /** Phiên bản schema sự kiện. */
    private final int eventVersion;

    /**
     * Khởi tạo sự kiện.
     *
     * @param operationId id operation
     * @param fromStatus  trạng thái trước
     * @param toStatus    trạng thái sau
     * @param actor       tác nhân
     * @param occurredAt  thời điểm sự kiện
     */
    public OperationAdvanced(UUID operationId, String fromStatus, String toStatus,
                             String actor, Instant occurredAt) {
        this.operationId = operationId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actor = actor;
        this.occurredAt = occurredAt;
        this.eventVersion = 1;
    }

    /** {@inheritDoc} */
    @Override public UUID operationId() { return operationId; }
    /** {@inheritDoc} */
    @Override public String eventType() { return "auditops.operation.advanced"; }
    /** {@inheritDoc} */
    @Override public int eventVersion() { return eventVersion; }
    /** {@inheritDoc} */
    @Override public Instant occurredAt() { return occurredAt; }

    /** @return trạng thái trước. */
    public String fromStatus() { return fromStatus; }
    /** @return trạng thái sau. */
    public String toStatus() { return toStatus; }
    /** @return tác nhân gây chuyển trạng thái. */
    public String actor() { return actor; }
}