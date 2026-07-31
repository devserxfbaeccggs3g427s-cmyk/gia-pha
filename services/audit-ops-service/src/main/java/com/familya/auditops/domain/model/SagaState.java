/**
 * Envelope trạng thái Saga.
 *
 * <p>Lưu giữ tên step hiện tại, cờ đang trong compensation, payload
 * phiên bản (JSON mờ) và bộ đếm version cho optimistic concurrency.</p>
 */
package com.familya.auditops.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root cho envelope Saga của một operation.
 */
public final class SagaState {

    /** UUID operation. */
    private final UUID operationId;
    /** Loại Saga. */
    private final String sagaType;
    /** Tên step hiện tại. */
    private String currentStep;
    /** Cờ đang trong compensation. */
    private boolean compensating;
    /** Số step của Saga. */
    private int stepCount;
    /** Thời điểm chuyển trạng thái gần nhất. */
    private Instant lastTransitionAt;
    /** Payload dạng map (immutable). */
    private final Map<String, Object> payload;
    /** Phiên bản cho optimistic concurrency. */
    private long version;

    /**
     * Khởi tạo envelope.
     *
     * @param operationId      UUID operation
     * @param sagaType         loại Saga
     * @param currentStep      step hiện tại
     * @param compensating     cờ compensation
     * @param stepCount        số step
     * @param lastTransitionAt thời điểm chuyển trạng thái gần nhất
     * @param payload          payload (immutable)
     * @param version          phiên bản
     */
    public SagaState(UUID operationId,
                     String sagaType,
                     String currentStep,
                     boolean compensating,
                     int stepCount,
                     Instant lastTransitionAt,
                     Map<String, Object> payload,
                     long version) {
        this.operationId = Objects.requireNonNull(operationId);
        this.sagaType = Objects.requireNonNull(sagaType);
        this.currentStep = Objects.requireNonNull(currentStep);
        this.compensating = compensating;
        this.stepCount = stepCount;
        this.lastTransitionAt = Objects.requireNonNull(lastTransitionAt);
        this.payload = payload == null ? Map.of() : Map.copyOf(payload);
        this.version = version;
    }

    /** @return UUID operation. */
    public UUID operationId() { return operationId; }
    /** @return loại Saga. */
    public String sagaType() { return sagaType; }
    /** @return step hiện tại. */
    public String currentStep() { return currentStep; }
    /** @return cờ compensation. */
    public boolean compensating() { return compensating; }
    /** @return số step. */
    public int stepCount() { return stepCount; }
    /** @return thời điểm chuyển trạng thái gần nhất. */
    public Instant lastTransitionAt() { return lastTransitionAt; }
    /** @return payload. */
    public Map<String, Object> payload() { return payload; }
    /** @return phiên bản. */
    public long version() { return version; }

    /**
     * Chuyển trạng thái envelope sang step mới.
     *
     * <p>Đồng thời cập nhật cờ compensation, thời điểm chuyển trạng
     * thái và tăng version.</p>
     *
     * @param nextStep    tên step tiếp theo
     * @param compensating cờ compensation
     * @param when        thời điểm chuyển
     */
    public void transition(String nextStep, boolean compensating, Instant when) {
        this.currentStep = nextStep;
        this.compensating = compensating;
        this.lastTransitionAt = when;
        this.version = this.version + 1;
    }

    /**
     * Tăng số step của Saga lên 1.
     */
    public void incrementStepCount() {
        this.stepCount = this.stepCount + 1;
    }
}