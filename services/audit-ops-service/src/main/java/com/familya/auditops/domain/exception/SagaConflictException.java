/**
 * Ngoại lệ khi máy trạng thái Saga từ chối transition được đề xuất.
 * Có thể do transition không hợp lệ hoặc phản hồi của participant
 * nhắm vào revision đã cũ.
 *
 * <p>Ánh xạ sang HTTP 409.</p>
 */
package com.familya.auditops.domain.exception;

import com.familya.platform.error.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Ngoại lệ chỉ ra xung đột khi áp dụng transition Saga.
 */
public class SagaConflictException extends DomainException {

    /**
     * Khởi tạo exception.
     *
     * @param message mô tả chi tiết xung đột
     */
    public SagaConflictException(String message) {
        super(HttpStatus.CONFLICT, "saga.conflict", message);
    }
}