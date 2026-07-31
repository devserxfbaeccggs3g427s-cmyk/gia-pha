/**
 * Trạng thái Saga step theo từng participant.
 *
 * <p>Phản chiếu {@link OperationStatus} nhưng có phạm vi một step.
 * Operation chỉ {@code SUCCEEDED} khi tất cả step bắt buộc đã ở
 * trạng thái {@link #ACKED}.</p>
 */
package com.familya.auditops.domain.model;

/**
 * Enum đại diện cho vòng đời của một Saga step.
 */
public enum StepStatus {

    /** Step vừa được tạo, chưa dispatch. */
    PENDING,
    /** Step đã được dispatch tới participant. */
    DISPATCHED,
    /** Step đã được participant ack (terminal tích cực). */
    ACKED,
    /** Step thất bại; orchestrator sẽ retry hoặc chuyển dead-letter. */
    FAILED,
    /** Step đã được compensate (terminal). */
    COMPENSATED,
    /** Step đã được chuyển vào dead-letter (terminal). */
    DEAD_LETTERED;

    /**
     * @return true nếu trạng thái này là terminal (không thể chuyển tiếp)
     */
    public boolean isTerminal() {
        return this == ACKED || this == COMPENSATED || this == DEAD_LETTERED;
    }
}