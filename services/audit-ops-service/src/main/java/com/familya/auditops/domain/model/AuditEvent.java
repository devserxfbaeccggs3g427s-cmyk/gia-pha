/**
 * Sự kiện audit chỉ-append (append-only).
 *
 * <p>Một khi đã được ghi, row <b>không được</b> cập nhật hay xoá bởi
 * mã ứng dụng. Việc lưu giữ được quản lý bởi scheduled job đã được
 * phê duyệt (Task 17 / ADR-007).</p>
 */
package com.familya.auditops.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root cho một sự kiện audit.
 *
 * <p>Các trường bắt buộc: {@code auditId}, {@code actorKind},
 * {@code action}, {@code occurredAt}. Các trường khác có thể null tuỳ
 * ngữ cảnh.</p>
 */
public final class AuditEvent {

    /**
     * Loại tác nhân gây ra sự kiện.
     * <ul>
     *   <li>{@link #USER}: người dùng cuối.</li>
     *   <li>{@link #OPERATOR}: operator vận hành.</li>
     *   <li>{@link #SERVICE}: dịch vụ tự động.</li>
     * </ul>
     */
    public enum ActorKind {
        USER, OPERATOR, SERVICE
    }

    /** UUID duy nhất của sự kiện audit. */
    private final UUID auditId;
    /** UUID operation liên quan (nếu có). */
    private final UUID operationId;
    /** Correlation id (nếu có). */
    private final UUID correlationId;
    /** UUID user thực hiện (nếu có). */
    private final UUID actorUserId;
    /** Loại tác nhân. */
    private final ActorKind actorKind;
    /** Tên hành động. */
    private final String action;
    /** Loại đối tượng bị tác động. */
    private final String targetType;
    /** ID đối tượng bị tác động. */
    private final String targetId;
    /** Chi tiết bổ sung (immutable). */
    private final Map<String, Object> detail;
    /** Thời điểm xảy ra. */
    private final Instant occurredAt;
    /** Mã trace (nếu có). */
    private final String traceId;

    /**
     * Khởi tạo sự kiện audit.
     *
     * @param auditId      UUID sự kiện
     * @param operationId  UUID operation
     * @param correlationId correlation id
     * @param actorUserId  UUID user
     * @param actorKind    loại tác nhân
     * @param action       tên hành động
     * @param targetType   loại đối tượng
     * @param targetId     id đối tượng
     * @param detail       chi tiết bổ sung
     * @param occurredAt   thời điểm xảy ra
     * @param traceId      mã trace
     */
    public AuditEvent(UUID auditId,
                      UUID operationId,
                      UUID correlationId,
                      UUID actorUserId,
                      ActorKind actorKind,
                      String action,
                      String targetType,
                      String targetId,
                      Map<String, Object> detail,
                      Instant occurredAt,
                      String traceId) {
        this.auditId = Objects.requireNonNull(auditId);
        this.operationId = operationId;
        this.correlationId = correlationId;
        this.actorUserId = actorUserId;
        this.actorKind = Objects.requireNonNull(actorKind);
        this.action = Objects.requireNonNull(action);
        this.targetType = targetType;
        this.targetId = targetId;
        // Map.copyOf để đảm bảo bất biến; tránh chia sẻ tham chiếu với caller.
        this.detail = detail == null ? Map.of() : Map.copyOf(detail);
        this.occurredAt = Objects.requireNonNull(occurredAt);
        this.traceId = traceId;
    }

    /** @return UUID sự kiện. */
    public UUID auditId() { return auditId; }
    /** @return UUID operation. */
    public UUID operationId() { return operationId; }
    /** @return correlation id. */
    public UUID correlationId() { return correlationId; }
    /** @return UUID user. */
    public UUID actorUserId() { return actorUserId; }
    /** @return loại tác nhân. */
    public ActorKind actorKind() { return actorKind; }
    /** @return tên hành động. */
    public String action() { return action; }
    /** @return loại đối tượng. */
    public String targetType() { return targetType; }
    /** @return id đối tượng. */
    public String targetId() { return targetId; }
    /** @return chi tiết bổ sung. */
    public Map<String, Object> detail() { return detail; }
    /** @return thời điểm xảy ra. */
    public Instant occurredAt() { return occurredAt; }
    /** @return mã trace. */
    public String traceId() { return traceId; }
}