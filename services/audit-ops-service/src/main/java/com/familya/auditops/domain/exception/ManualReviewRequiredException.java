/**
 * Ngoại lệ được ném khi orchestrator xác định Saga không thể hoàn
 * thành mà không có sự can thiệp của operator.
 *
 * <p>Ánh xạ sang HTTP 409 với mã lỗi {@code saga.manual_review}.</p>
 */
package com.familya.auditops.domain.exception;

import com.familya.platform.error.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Ngoại lệ {@link DomainException} chỉ ra rằng operation cần được
 * operator xem xét thủ công.
 */
public class ManualReviewRequiredException extends DomainException {

    /**
     * Khởi tạo exception với thông điệp.
     *
     * @param message mô tả lý do cần manual review
     */
    public ManualReviewRequiredException(String message) {
        super(HttpStatus.CONFLICT, "saga.manual_review", message);
    }
}