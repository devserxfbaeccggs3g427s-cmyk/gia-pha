package com.familya.identity.domain.exception;

import com.familya.platform.error.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Ngoại lệ domain được ném khi số lượt đăng ký vượt quá giới hạn cho
 * phép trong một khoảng thời gian (mặc định: theo giờ, theo IP).
 *
 * <p>Tương ứng với HTTP {@code 429 Too Many Requests} và mã lỗi
 * {@code rate.limit.exceeded}. Thông điệp lỗi nên chứa thông tin về
 * IP và/hoặc thời điểm retry để client hành xử phù hợp.
 *
 * @author Familya Platform Team
 * @since 1.0.0
 */
public class RateLimitExceededException extends DomainException {
    /**
     * Khởi tạo ngoại lệ với thông điệp mô tả.
     *
     * @param message mô tả lý do vượt giới hạn (thường kèm IP và/hoặc thời điểm retry).
     */
    public RateLimitExceededException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, "rate.limit.exceeded", message);
    }
}
