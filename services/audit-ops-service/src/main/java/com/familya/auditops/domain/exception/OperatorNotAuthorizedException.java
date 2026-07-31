/**
 * Ngoại lệ khi operator cố thực hiện hành động yêu cầu role admin,
 * hoặc cố tác động lên operation không thuộc quyền của họ.
 *
 * <p>Ánh xạ sang HTTP 403.</p>
 */
package com.familya.auditops.domain.exception;

import com.familya.platform.error.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Ngoại lệ chỉ ra operator không có quyền thực hiện hành động.
 */
public class OperatorNotAuthorizedException extends DomainException {

    /**
     * Khởi tạo exception.
     *
     * @param message mô tả lý do từ chối
     */
    public OperatorNotAuthorizedException(String message) {
        super(HttpStatus.FORBIDDEN, "operator.unauthorized", message);
    }
}