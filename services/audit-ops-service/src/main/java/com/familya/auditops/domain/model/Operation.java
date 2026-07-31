/**
 * Aggregate root cho Operation.
 *
 * <p>Một thể hiện ứng với mỗi thay đổi xuyên service được bounded
 * context sở hữu ghi nhận. Aggregate này là projection công khai mà
 * Gateway polling ({@code GET /api/v2/operations/{id}}) và operator
 * console đọc để thực hiện retry/manual-review.</p>
 *
 * <p>Dịch vụ này <b>KHÔNG</b> phải cơ quan có thẩm quyền nghiệp vụ
 * cho bất kỳ domain nào - nó chỉ sao chép các chuyển trạng thái khi
 * các sự kiện đến trên Saga reply topic. Bounded context sở hữu giữ
 * nguồn dữ liệu sự thật trong database riêng; projection này tồn tại
 * phục vụ polling, audit và operator tooling (ADR-003).</p>
 */
package com.familya.auditops.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root của Operation.
 *
 * <p>Các trường bất biến sau khi khởi tạo: {@code id}, {@code service},
 * {@code operationType}, {@code aggregateType}, {@code aggregateId},
 * {@code treeId}, {@code actingUser}, {@code detail}, {@code startedAt}.
 * Các trường có thể thay đổi qua {@link #applyTransition}:
 * {@code status}, {@code updatedAt}, {@code finishedAt}, {@code version}.</p>
 */
public final class Operation {

    /** UUID operation. */
    private final UUID id;
    /** Correlation id. */
    private final UUID correlationId;
    /** Tên bounded context sở hữu. */
    private final String service;
    /** Loại operation. */
    private final String operationType;
    /** Trạng thái hiện tại. */
    private OperationStatus status;
    /** Revision mục tiêu. */
    private final Long targetRevision;
    /** Epoch mục tiêu. */
    private final Long targetEpoch;
    /** Loại aggregate. */
    private final String aggregateType;
    /** ID aggregate. */
    private final String aggregateId;
    /** UUID cây. */
    private final UUID treeId;
    /** UUID user thực hiện. */
    private final UUID actingUser;
    /** Chi tiết bổ sung (immutable). */
    private final Map<String, Object> detail;
    /** Mã lỗi (nếu có). */
    private final String errorCode;
    /** Thông điệp lỗi (nếu có). */
    private final String errorMessage;
    /** Thời điểm bắt đầu. */
    private final Instant startedAt;
    /** Thời điểm cập nhật gần nhất. */
    private Instant updatedAt;
    /** Thời điểm kết thúc (nếu đã terminal). */
    private Instant finishedAt;
    /** Phiên bản cho optimistic concurrency. */
    private long version;

    /**
     * Khởi tạo operation.
     *
     * @param id              UUID operation
     * @param correlationId   correlation id
     * @param service         tên bounded context sở hữu
     * @param operationType   loại operation
     * @param status          trạng thái ban đầu
     * @param targetRevision  revision mục tiêu
     * @param targetEpoch     epoch mục tiêu
     * @param aggregateType   loại aggregate
     * @param aggregateId     id aggregate
     * @param treeId          id cây
     * @param actingUser      id user thực hiện
     * @param detail          chi tiết bổ sung
     * @param errorCode       mã lỗi
     * @param errorMessage    thông điệp lỗi
     * @param startedAt       thời điểm bắt đầu
     * @param updatedAt       thời điểm cập nhật
     * @param finishedAt      thời điểm kết thúc
     * @param version         phiên bản
     */
    public Operation(UUID id,
                     UUID correlationId,
                     String service,
                     String operationType,
                     OperationStatus status,
                     Long targetRevision,
                     Long targetEpoch,
                     String aggregateType,
                     String aggregateId,
                     UUID treeId,
                     UUID actingUser,
                     Map<String, Object> detail,
                     String errorCode,
                     String errorMessage,
                     Instant startedAt,
                     Instant updatedAt,
                     Instant finishedAt,
                     long version) {
        this.id = Objects.requireNonNull(id);
        this.correlationId = correlationId;
        this.service = Objects.requireNonNull(service);
        this.operationType = Objects.requireNonNull(operationType);
        this.status = Objects.requireNonNull(status);
        this.targetRevision = targetRevision;
        this.targetEpoch = targetEpoch;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.treeId = treeId;
        this.actingUser = actingUser;
        this.detail = detail == null ? Map.of() : Map.copyOf(detail);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.startedAt = Objects.requireNonNull(startedAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.finishedAt = finishedAt;
        this.version = version;
    }

    /** @return UUID operation. */
    public UUID id() { return id; }
    /** @return correlation id. */
    public UUID correlationId() { return correlationId; }
    /** @return tên bounded context sở hữu. */
    public String service() { return service; }
    /** @return loại operation. */
    public String operationType() { return operationType; }
    /** @return trạng thái hiện tại. */
    public OperationStatus status() { return status; }
    /** @return revision mục tiêu. */
    public Long targetRevision() { return targetRevision; }
    /** @return epoch mục tiêu. */
    public Long targetEpoch() { return targetEpoch; }
    /** @return loại aggregate. */
    public String aggregateType() { return aggregateType; }
    /** @return id aggregate. */
    public String aggregateId() { return aggregateId; }
    /** @return id cây. */
    public UUID treeId() { return treeId; }
    /** @return id user thực hiện. */
    public UUID actingUser() { return actingUser; }
    /** @return chi tiết bổ sung. */
    public Map<String, Object> detail() { return detail; }
    /** @return mã lỗi. */
    public String errorCode() { return errorCode; }
    /** @return thông điệp lỗi. */
    public String errorMessage() { return errorMessage; }
    /** @return thời điểm bắt đầu. */
    public Instant startedAt() { return startedAt; }
    /** @return thời điểm cập nhật gần nhất. */
    public Instant updatedAt() { return updatedAt; }
    /** @return thời điểm kết thúc (nếu có). */
    public Instant finishedAt() { return finishedAt; }
    /** @return phiên bản hiện tại. */
    public long version() { return version; }

    /**
     * Áp dụng một transition trạng thái trong bộ nhớ.
     *
     * <p>Caller chịu trách nhiệm lưu trạng thái mới qua repository và
     * phải đã xác minh transition hợp lệ với máy trạng thái Saga.
     * {@code updatedAt} do caller cung cấp để aggregate độc lập với
     * framework.</p>
     *
     * @param next      trạng thái mới
     * @param updatedAt thời điểm cập nhật
     */
    public void applyTransition(OperationStatus next,
                                 Instant updatedAt) {
        this.status = next;
        this.version = this.version + 1;
        this.updatedAt = updatedAt;
        // Nếu trạng thái mới là terminal thì ghi nhận finishedAt.
        if (next.isTerminal()) {
            this.finishedAt = updatedAt;
        }
    }

    /**
     * Hàm tiện ích chuyển sang {@link com.familya.platform.api.AsyncOperation}
     * cho projection polling.
     *
     * <p>Các trường được ánh xạ:</p>
     * <ul>
     *   <li>{@code status}: ánh xạ 1-1 sang {@code AsyncOperation.Status}.</li>
     *   <li>{@code detail}: chỉ trả về khi SUCCEEDED.</li>
     *   <li>{@code errorBody}: chỉ trả về khi FAILED hoặc MANUAL_REVIEW.</li>
     * </ul>
     *
     * @return envelope cho client polling
     */
    public com.familya.platform.api.AsyncOperation toAsyncOperation() {
        com.familya.platform.api.AsyncOperation.Status api =
                com.familya.platform.api.AsyncOperation.Status.valueOf(status.name());
        return new com.familya.platform.api.AsyncOperation(
                id, api,
                "/api/v2/operations/" + id,
                status == OperationStatus.SUCCEEDED ? detail : null,
                status == OperationStatus.FAILED || status == OperationStatus.MANUAL_REVIEW
                        ? new com.familya.platform.api.AsyncOperation.ErrorBody(
                                errorCode == null ? "operation.failed" : errorCode,
                                errorMessage == null ? "Operation did not succeed" : errorMessage,
                                null, null)
                        : null,
                null,
                updatedAt);
    }
}