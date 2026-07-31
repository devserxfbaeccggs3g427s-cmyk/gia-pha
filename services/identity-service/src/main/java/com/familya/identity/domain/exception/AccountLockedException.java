package com.familya.identity.domain.exception;

import com.familya.platform.error.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Ngoại lệ domain được ném khi tài khoản người dùng đang bị khóa.
 *
 * <p>Ngoại lệ này tương ứng với HTTP {@code 423 Locked} và mã lỗi
 * {@code account.locked} – phù hợp với chuẩn WebDAV (RFC 4918) cho
 * tài nguyên bị khóa. Thông điệp lỗi nên chứa thời điểm dự kiến
 * mở khóa để client hiển thị cho người dùng.
 *
 * @author Familya Platform Team
 * @since 1.0.0
 */
public class AccountLockedException extends DomainException {
    /**
     * Khởi tạo ngoại lệ với thông điệp mô tả.
     *
     * @param message mô tả lý do tài khoản bị khóa (thường kèm thời điểm mở khóa).
     */
    public AccountLockedException(String message) {
        super(HttpStatus.LOCKED, "account.locked", message);
    }
}
