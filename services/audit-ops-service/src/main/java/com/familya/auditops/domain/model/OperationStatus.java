/**
 * Vòng đời chuẩn của Saga (ADR-007 / Task 13).
 *
 * <p>Các đường đi:</p>
 * <ul>
 *   <li>Đường thẳng: {@code PENDING → RUNNING → SUCCEEDED}.</li>
 *   <li>Đường thất bại: {@code RUNNING → FAILED → COMPENSATING → COMPENSATED}.</li>
 *   <li>Đường operator: bất kỳ trạng thái chưa kết thúc có thể chuyển
 *       sang {@code MANUAL_REVIEW} khi orchestrator phát hiện điều
 *       kiện không thể phục hồi (participant outage ngoài policy,
 *       payload poison, vi phạm ranh giới không thể đảo).</li>
 * </ul>
 */
package com.familya.auditops.domain.model;

/**
 * Enum đại diện cho vòng đời operation.
 */
public enum OperationStatus {

    /** Operation vừa được tạo, chưa có step nào được xử lý. */
    PENDING,
    /** Operation đang được xử lý bởi các participant. */
    RUNNING,
    /** Operation đã hoàn thành thành công (terminal). */
    SUCCEEDED,
    /** Operation thất bại, sẽ được compensate. */
    FAILED,
    /** Compensation đang được thực hiện. */
    COMPENSATING,
    /** Compensation đã hoàn tất (terminal). */
    COMPENSATED,
    /** Cần operator can thiệp (terminal). */
    MANUAL_REVIEW;

    /**
     * @return true nếu trạng thái này là terminal (không thể chuyển tiếp)
     */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == COMPENSATED || this == MANUAL_REVIEW;
    }

    /**
     * @return true nếu operation đang trong hoặc sau giai đoạn compensation
     */
    public boolean isCompensating() {
        return this == COMPENSATING || this == COMPENSATED;
    }

    /**
     * @return true nếu operation đã rơi vào nhánh thất bại (bao gồm
     *         compensation và manual review)
     */
    public boolean isFailed() {
        return this == FAILED || this == COMPENSATING || this == COMPENSATED || this == MANUAL_REVIEW;
    }
}