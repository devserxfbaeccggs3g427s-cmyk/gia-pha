package com.familya.platform.error;

import org.springframework.http.HttpStatus;

/**
 * Ngoại lệ được ném ra khi một idempotency key được tái sử dụng với một payload
 * khác so với lần ghi nhận đầu tiên.
 *
 * <p>Được {@link GlobalErrorHandler} ánh xạ sang phản hồi HTTP {@code 409 CONFLICT}
 * với mã lỗi ổn định {@code "idempotency.conflict"}. Đây là cơ chế bảo vệ chống
 * lạm dụng: client cố tình (hoặc do lỗi logic) gửi cùng một key nhưng với nội
 * dung khác, hệ thống sẽ từ chối thay vì âm thầm ghi đè.</p>
 *
 * <p>Quy tắc xử lý chuẩn:</p>
 * <ul>
 *   <li>Cùng key + cùng payload hash: trả về kết quả đã ghi nhận trước đó.</li>
 *   <li>Cùng key + payload hash khác: ném {@link IdempotencyConflictException}.</li>
 * </ul>
 *
 * @author Family Tree Platform Team
 */
public class IdempotencyConflictException extends DomainException {

    /**
     * Khởi tạo ngoại lệ với thông điệp mô tả cụ thể.
     *
     * @param message thông điệp mô tả xung đột (ví dụ: "Idempotency key reused with a different payload hash")
     */
    public IdempotencyConflictException(String message) {
        // Gọi constructor lớp cha với HTTP 409 và mã lỗi "idempotency.conflict".
        super(HttpStatus.CONFLICT, "idempotency.conflict", message);
    }
}
