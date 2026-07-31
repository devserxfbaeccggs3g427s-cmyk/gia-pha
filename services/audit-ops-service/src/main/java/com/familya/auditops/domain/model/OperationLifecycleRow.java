/**
 * Projection vòng đời operation cho operator UI.
 *
 * <p>Row này <b>không</b> có thẩm quyền - bounded context sở hữu Saga
 * publish các sự kiện {@code OperationStarted} /
 * {@code OperationStateChanged} và Audit Ops tiêu thụ. Xem ADR-003.</p>
 */
package com.familya.auditops.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Bản ghi projection vòng đời operation.
 *
 * <p>Mỗi row tương ứng với một operation đang được theo dõi; được
 * cập nhật bởi {@code OperationLifecycleProjectionListener} khi có
 * sự kiện từ topic {@code operations.events.v1}.</p>
 */
public final class OperationLifecycleRow {

    /** UUID operation. */
    private final UUID operationId;
    /** Tên bounded context sở hữu. */
    private final String ownerService;
    /** Loại Saga. */
    private final String sagaType;
    /** UUID cây gia phả (nếu có). */
    private final UUID treeId;
    /** UUID user khởi tạo (nếu có). */
    private final UUID initiatingUserId;
    /** Trạng thái hiện tại. */
    private final String state;
    /** Version mục tiêu. */
    private final Long targetVersion;
    /** Epoch mục tiêu. */
    private final Long targetEpoch;
    /** Mã lỗi (nếu có). */
    private final String failureCode;
    /** Thông điệp lỗi (nếu có). */
    private final String failureMessage;
    /** Định tuyến lỗi (FAILED/COMPENSATING/DLQ/...). */
    private final String failureRouting;
    /** Thời điểm bắt đầu. */
    private final Instant startedAt;
    /** Thời điểm cập nhật gần nhất. */
    private final Instant updatedAt;
    /** Thời điểm kết thúc (nếu có). */
    private final Instant finalizedAt;

    /**
     * Khởi tạo row.
     *
     * @param operationId      UUID operation
     * @param ownerService     tên service sở hữu
     * @param sagaType         loại Saga
     * @param treeId           id cây
     * @param initiatingUserId id user khởi tạo
     * @param state            trạng thái
     * @param targetVersion    version mục tiêu
     * @param targetEpoch      epoch mục tiêu
     * @param failureCode      mã lỗi
     * @param failureMessage   thông điệp lỗi
     * @param failureRouting   định tuyến lỗi
     * @param startedAt        thời điểm bắt đầu
     * @param updatedAt        thời điểm cập nhật
     * @param finalizedAt      thời điểm kết thúc
     */
    public OperationLifecycleRow(UUID operationId, String ownerService, String sagaType,
                                 UUID treeId, UUID initiatingUserId, String state,
                                  Long targetVersion, Long targetEpoch,
                                  String failureCode, String failureMessage, String failureRouting,
                                  Instant startedAt, Instant updatedAt, Instant finalizedAt) {
        this.operationId = Objects.requireNonNull(operationId);
        this.ownerService = Objects.requireNonNull(ownerService);
        this.sagaType = Objects.requireNonNull(sagaType);
        this.treeId = treeId;
        this.initiatingUserId = initiatingUserId;
        this.state = Objects.requireNonNull(state);
        this.targetVersion = targetVersion;
        this.targetEpoch = targetEpoch;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.failureRouting = failureRouting;
        this.startedAt = Objects.requireNonNull(startedAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.finalizedAt = finalizedAt;
    }

    /** @return UUID operation. */
    public UUID operationId() { return operationId; }
    /** @return tên service sở hữu. */
    public String ownerService() { return ownerService; }
    /** @return loại Saga. */
    public String sagaType() { return sagaType; }
    /** @return id cây. */
    public UUID treeId() { return treeId; }
    /** @return id user khởi tạo. */
    public UUID initiatingUserId() { return initiatingUserId; }
    /** @return trạng thái. */
    public String state() { return state; }
    /** @return version mục tiêu. */
    public Long targetVersion() { return targetVersion; }
    /** @return epoch mục tiêu. */
    public Long targetEpoch() { return targetEpoch; }
    /** @return mã lỗi. */
    public String failureCode() { return failureCode; }
    /** @return thông điệp lỗi. */
    public String failureMessage() { return failureMessage; }
    /** @return định tuyến lỗi. */
    public String failureRouting() { return failureRouting; }
    /** @return thời điểm bắt đầu. */
    public Instant startedAt() { return startedAt; }
    /** @return thời điểm cập nhật. */
    public Instant updatedAt() { return updatedAt; }
    /** @return thời điểm kết thúc. */
    public Instant finalizedAt() { return finalizedAt; }
}