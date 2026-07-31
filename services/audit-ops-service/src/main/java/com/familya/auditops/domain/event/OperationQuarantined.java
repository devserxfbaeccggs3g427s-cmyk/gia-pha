/**
 * Sự kiện phát ra khi operation bị quarantine (dead-letter).
 *
 * <p>Loại sự kiện: {@code "auditops.operation.quarantined"}.</p>
 */
package com.familya.auditops.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Sự kiện thông báo rằng operation đã bị đưa vào dead-letter và cần
 * operator xem xét thủ công.
 */
public final class OperationQuarantined extends AuditOpsEvent {

    /** UUID của operation. */
    private final UUID operationId;
    /** Lý do quarantine (mã lỗi + thông điệp). */
    private final String reason;
    /** Thời điểm sự kiện. */
    private final Instant occurredAt;
    /** Phiên bản schema. */
    private final int eventVersion;

    /**
     * Khởi tạo sự kiện.
     *
     * @param operationId id operation
     * @param reason      lý do quarantine
     * @param occurredAt  thời điểm sự kiện
     */
    public OperationQuarantined(UUID operationId, String reason, Instant occurredAt) {
        this.operationId = operationId;
        this.reason = reason;
        this.occurredAt = occurredAt;
        this.eventVersion = 1;
    }

    /** {@inheritDoc} */
    @Override public UUID operationId() { return operationId; }
    /** {@inheritDoc} */
    @Override public String eventType() { return "auditops.operation.quarantined"; }
    /** {@inheritDoc} */
    @Override public int eventVersion() { return eventVersion; }
    /** {@inheritDoc} */
    @Override public Instant occurredAt() { return occurredAt; }

    /** @return lý do quarantine. */
    public String reason() { return reason; }
}