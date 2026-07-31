/**
 * Saga step. Một row cho mỗi (operation, participant, stepName).
 *
 * <p>{@code expectedVersion} là version mà orchestrator đọc từ command
 * gốc tại thời điểm dispatch. Participant <b>BẮT BUỘC</b> phải ack
 * với {@code targetRevision} &gt;= {@code expectedVersion} và
 * {@code targetEpoch} &gt;= {@code step.targetEpoch} để step được
 * tính là {@link StepStatus#ACKED}.</p>
 */
package com.familya.auditops.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root cho một Saga step.
 */
public final class SagaStep {

    /** UUID operation. */
    private final UUID operationId;
    /** Tên bounded context participant. */
    private final String participantService;
    /** Tên step trong Saga. */
    private final String stepName;
    /** Thứ tự step trong Saga. */
    private final int sequenceNo;
    /** Trạng thái hiện tại. */
    private StepStatus status;
    /** Revision mục tiêu. */
    private final Long targetRevision;
    /** Epoch mục tiêu. */
    private final Long targetEpoch;
    /** Version mong đợi. */
    private final Long expectedVersion;
    /** Thời điểm ack (nếu có). */
    private Instant ackedAt;
    /** Số lần đã thử. */
    private int attemptCount;
    /** Mã lỗi lần gần nhất. */
    private String lastErrorCode;
    /** Thông điệp lỗi lần gần nhất. */
    private String lastErrorMessage;
    /** Thời điểm thử gần nhất. */
    private Instant lastAttemptedAt;
    /** Chi tiết bổ sung (immutable). */
    private final Map<String, Object> detail;

    /**
     * Khởi tạo step.
     *
     * @param operationId       UUID operation
     * @param participantService tên participant
     * @param stepName          tên step
     * @param sequenceNo        thứ tự
     * @param status            trạng thái ban đầu
     * @param targetRevision    revision mục tiêu
     * @param targetEpoch       epoch mục tiêu
     * @param expectedVersion   version mong đợi
     * @param ackedAt           thời điểm ack
     * @param attemptCount      số lần thử
     * @param lastErrorCode     mã lỗi gần nhất
     * @param lastErrorMessage  thông điệp lỗi gần nhất
     * @param lastAttemptedAt   thời điểm thử gần nhất
     * @param detail            chi tiết bổ sung
     */
    public SagaStep(UUID operationId,
                    String participantService,
                    String stepName,
                    int sequenceNo,
                    StepStatus status,
                    Long targetRevision,
                    Long targetEpoch,
                    Long expectedVersion,
                    Instant ackedAt,
                    int attemptCount,
                    String lastErrorCode,
                    String lastErrorMessage,
                    Instant lastAttemptedAt,
                    Map<String, Object> detail) {
        this.operationId = Objects.requireNonNull(operationId);
        this.participantService = Objects.requireNonNull(participantService);
        this.stepName = Objects.requireNonNull(stepName);
        this.sequenceNo = sequenceNo;
        this.status = Objects.requireNonNull(status);
        this.targetRevision = targetRevision;
        this.targetEpoch = targetEpoch;
        this.expectedVersion = expectedVersion;
        this.ackedAt = ackedAt;
        this.attemptCount = attemptCount;
        this.lastErrorCode = lastErrorCode;
        this.lastErrorMessage = lastErrorMessage;
        this.lastAttemptedAt = lastAttemptedAt;
        this.detail = detail == null ? Map.of() : Map.copyOf(detail);
    }

    /** @return UUID operation. */
    public UUID operationId() { return operationId; }
    /** @return tên participant. */
    public String participantService() { return participantService; }
    /** @return tên step. */
    public String stepName() { return stepName; }
    /** @return thứ tự. */
    public int sequenceNo() { return sequenceNo; }
    /** @return trạng thái. */
    public StepStatus status() { return status; }
    /** @return revision mục tiêu. */
    public Long targetRevision() { return targetRevision; }
    /** @return epoch mục tiêu. */
    public Long targetEpoch() { return targetEpoch; }
    /** @return version mong đợi. */
    public Long expectedVersion() { return expectedVersion; }
    /** @return thời điểm ack. */
    public Instant ackedAt() { return ackedAt; }
    /** @return số lần thử. */
    public int attemptCount() { return attemptCount; }
    /** @return mã lỗi gần nhất. */
    public String lastErrorCode() { return lastErrorCode; }
    /** @return thông điệp lỗi gần nhất. */
    public String lastErrorMessage() { return lastErrorMessage; }
    /** @return thời điểm thử gần nhất. */
    public Instant lastAttemptedAt() { return lastAttemptedAt; }
    /** @return chi tiết bổ sung. */
    public Map<String, Object> detail() { return detail; }

    /**
     * Đánh dấu step vừa được dispatch tới participant.
     *
     * <p>Tăng {@code attemptCount} và cập nhật {@code lastAttemptedAt}.</p>
     *
     * @param when thời điểm dispatch
     */
    public void markDispatched(Instant when) {
        this.status = StepStatus.DISPATCHED;
        this.attemptCount = this.attemptCount + 1;
        this.lastAttemptedAt = when;
    }

    /**
     * Đánh dấu step đã được participant ack.
     *
     * @param when thời điểm ack
     */
    public void markAcked(Instant when) {
        this.status = StepStatus.ACKED;
        this.ackedAt = when;
    }

    /**
     * Đánh dấu step thất bại.
     *
     * @param code    mã lỗi
     * @param message thông điệp lỗi
     * @param when    thời điểm
     */
    public void markFailed(String code, String message, Instant when) {
        this.status = StepStatus.FAILED;
        this.lastErrorCode = code;
        this.lastErrorMessage = message;
        this.lastAttemptedAt = when;
    }

    /**
     * Đánh dấu step đã được compensate.
     *
     * @param when thời điểm compensate
     */
    public void markCompensated(Instant when) {
        this.status = StepStatus.COMPENSATED;
        this.lastAttemptedAt = when;
    }

    /**
     * Đánh dấu step đã được đưa vào dead-letter.
     *
     * @param code    mã lỗi
     * @param message thông điệp lỗi
     * @param when    thời điểm
     */
    public void markDeadLettered(String code, String message, Instant when) {
        this.status = StepStatus.DEAD_LETTERED;
        this.lastErrorCode = code;
        this.lastErrorMessage = message;
        this.lastAttemptedAt = when;
    }
}