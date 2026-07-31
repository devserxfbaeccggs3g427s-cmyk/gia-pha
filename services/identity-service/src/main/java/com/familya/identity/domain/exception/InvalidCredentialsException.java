package com.familya.identity.domain.exception;

import com.familya.platform.error.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Ngoại lệ domain được ném khi thông tin đăng nhập không hợp lệ.
 *
 * <p>Tương ứng với HTTP {@code 401 Unauthorized} và mã lỗi
 * {@code credentials.invalid}. Lưu ý rằng thông điệp lỗi <strong>không
 * phân biệt</strong> giữa email không tồn tại và mật khẩu sai – đây
 * là nguyên tắc bảo mật giúp kẻ tấn công không dò được email hợp lệ.
 *
 * @author Familya Platform Team
 * @since 1.0.0
 */
public class InvalidCredentialsException extends DomainException {
    /**
     * Khởi tạo ngoại lệ với thông điệp mặc định.
     */
    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "credentials.invalid", "Invalid email or password.");
    }
}
