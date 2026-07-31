/**
 * Ngoại lệ khi operation không tồn tại.
 *
 * <p>Ánh xạ sang HTTP 404 thông qua {@code GlobalErrorHandler} của
 * platform.</p>
 */
package com.familya.auditops.domain.exception;

import com.familya.platform.error.NotFoundException;

/**
 * Ngoại lệ chỉ ra rằng không tìm thấy operation với id tương ứng.
 */
public class OperationNotFoundException extends NotFoundException {

    /**
     * Khởi tạo exception.
     *
     * @param operationId id operation không tồn tại
     */
    public OperationNotFoundException(String operationId) {
        super("Operation " + operationId + " was not found.");
    }
}