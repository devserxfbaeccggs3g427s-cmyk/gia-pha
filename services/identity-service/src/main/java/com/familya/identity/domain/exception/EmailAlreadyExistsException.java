package com.familya.identity.domain.exception;

import com.familya.platform.error.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Ngoại lệ domain được ném khi người dùng cố đăng ký với email đã tồn
 * tại trong hệ thống.
 *
 * <p>Tương ứng với HTTP {@code 409 Conflict} và mã lỗi {@code email.exists}.
 * Thông điệp lỗi đã được che email (chỉ trả về giá trị đã chuẩn hóa)
 * nên cần cân nhắc không làm lộ thông tin nhạy cảm khi log.
 *
 * @author Familya Platform Team
 * @since 1.0.0
 */
public class EmailAlreadyExistsException extends DomainException {
    /**
     * Khởi tạo ngoại lệ.
     *
     * @param email email đã chuẩn hóa bị trùng lặp.
     */
    public EmailAlreadyExistsException(String email) {
        super(HttpStatus.CONFLICT, "email.exists", "Email already registered: " + email);
    }
}
