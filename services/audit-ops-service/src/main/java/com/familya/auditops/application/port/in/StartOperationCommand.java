/**
 * Command bắt đầu một operation mới xuyên service.
 *
 * <p>Được phát ra bởi bounded context sở hữu thông qua port
 * {@code OperationService}. Kết quả trả về gồm UUID operation mới và
 * correlation id được gán.</p>
 */
package com.familya.auditops.application.port.in;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Command đóng gói toàn bộ tham số cần thiết để đăng ký operation.
 *
 * <p>Các trường bắt buộc: {@code operationType}. Các trường khác có
 * thể null tuỳ vào ngữ cảnh nghiệp vụ.</p>
 */
public final class StartOperationCommand {

    /** UUID của user thực hiện (nếu có). */
    private final UUID actingUser;
    /** Loại operation (bắt buộc). */
    private final String operationType;
    /** Loại aggregate (nếu có). */
    private final String aggregateType;
    /** ID của aggregate (nếu có). */
    private final String aggregateId;
    /** UUID cây gia phả liên quan. */
    private final UUID treeId;
    /** Revision mục tiêu. */
    private final Long targetRevision;
    /** Epoch mục tiêu. */
    private final Long targetEpoch;
    /** Chi tiết bổ sung (immutable copy). */
    private final Map<String, Object> detail;
    /** Khoá idempotency. */
    private final String idempotencyKey;
    /** Hash SHA-256 của payload phục vụ idempotency. */
    private final String payloadHash;
    /** Correlation id từ header. */
    private final String correlationIdHeader;
    /** Thời điểm bắt đầu phía client (hoặc null nếu không cung cấp). */
    private final Instant clientStartedAt;

    /**
     * Khởi tạo command.
     *
     * @param actingUser         user thực hiện
     * @param operationType      loại operation (bắt buộc)
     * @param aggregateType      loại aggregate
     * @param aggregateId        id aggregate
     * @param treeId             id cây
     * @param targetRevision     revision mục tiêu
     * @param targetEpoch        epoch mục tiêu
     * @param detail             chi tiết bổ sung
     * @param idempotencyKey     khoá idempotency
     * @param payloadHash        hash payload
     * @param correlationIdHeader correlation id từ header
     * @param clientStartedAt    thời điểm bắt đầu phía client
     */
    public StartOperationCommand(UUID actingUser,
                                 String operationType,
                                 String aggregateType,
                                 String aggregateId,
                                 UUID treeId,
                                 Long targetRevision,
                                 Long targetEpoch,
                                 Map<String, Object> detail,
                                 String idempotencyKey,
                                 String payloadHash,
                                 String correlationIdHeader,
                                 Instant clientStartedAt) {
        this.actingUser = actingUser;
        this.operationType = Objects.requireNonNull(operationType);
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.treeId = treeId;
        this.targetRevision = targetRevision;
        this.targetEpoch = targetEpoch;
        // Map.copyOf để đảm bảo bất biến và tránh chia sẻ tham chiếu với caller.
        this.detail = detail == null ? Map.of() : Map.copyOf(detail);
        this.idempotencyKey = idempotencyKey;
        this.payloadHash = payloadHash;
        this.correlationIdHeader = correlationIdHeader;
        this.clientStartedAt = clientStartedAt;
    }

    /** @return user thực hiện. */
    public UUID actingUser() { return actingUser; }
    /** @return loại operation. */
    public String operationType() { return operationType; }
    /** @return loại aggregate. */
    public String aggregateType() { return aggregateType; }
    /** @return id aggregate. */
    public String aggregateId() { return aggregateId; }
    /** @return id cây. */
    public UUID treeId() { return treeId; }
    /** @return revision mục tiêu. */
    public Long targetRevision() { return targetRevision; }
    /** @return epoch mục tiêu. */
    public Long targetEpoch() { return targetEpoch; }
    /** @return chi tiết bổ sung (immutable). */
    public Map<String, Object> detail() { return detail; }
    /** @return khoá idempotency. */
    public String idempotencyKey() { return idempotencyKey; }
    /** @return hash payload. */
    public String payloadHash() { return payloadHash; }
    /** @return correlation id từ header. */
    public String correlationIdHeader() { return correlationIdHeader; }
    /** @return thời điểm bắt đầu phía client. */
    public Instant clientStartedAt() { return clientStartedAt; }
}