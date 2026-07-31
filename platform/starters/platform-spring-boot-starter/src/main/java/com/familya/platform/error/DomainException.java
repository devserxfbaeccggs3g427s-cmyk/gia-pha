package com.familya.platform.error;

import org.springframework.http.HttpStatus;

/**
 * Lớp cơ sở cho mọi ngoại lệ nghiệp vụ (domain exception) được định nghĩa bởi
 * các dịch vụ trong nền tảng.
 *
 * <p>Mỗi ngoại lệ nghiệp vụ mang theo hai thông tin quan trọng:</p>
 * <ul>
 *   <li>{@link #getStatus()} — mã trạng thái HTTP tương ứng.</li>
 *   <li>{@link #getCode()} — mã lỗi ổn định theo máy, dùng trong
 *       {@link com.familya.platform.api.ErrorResponse}.</li>
 * </ul>
 *
 * <p>{@link GlobalErrorHandler} sẽ tự động ánh xạ các lớp con của {@code DomainException}
 * sang phản hồi HTTP phù hợp nhờ hai thông tin này. Nhờ đó các dịch vụ không cần
 * đăng ký handler riêng cho từng loại lỗi mà chỉ cần tạo lớp con phù hợp.</p>
 *
 * <p>Lớp này là {@code abstract} — chỉ được mở rộng, không thể khởi tạo trực tiếp.</p>
 *
 * @author Family Tree Platform Team
 */
public abstract class DomainException extends RuntimeException {

    /** Mã trạng thái HTTP sẽ trả về cho client. */
    private final HttpStatus status;

    /** Mã lỗi ổn định theo máy, dùng cho {@link com.familya.platform.api.ErrorResponse}. */
    private final String code;

    /**
     * Khởi tạo ngoại lệ với trạng thái, mã lỗi và thông điệp.
     *
     * @param status  mã trạng thái HTTP
     * @param code    mã lỗi ổn định theo máy
     * @param message thông điệp mô tả lỗi
     */
    protected DomainException(HttpStatus status, String code, String message) {
        // Gọi constructor RuntimeException để lưu thông điệp.
        super(message);
        this.status = status;
        this.code = code;
    }

    /**
     * Khởi tạo ngoại lệ với nguyên nhân gốc (cause).
     *
     * @param status  mã trạng thái HTTP
     * @param code    mã lỗi ổn định theo máy
     * @param message thông điệp mô tả lỗi
     * @param cause   nguyên nhân gốc (ngoại lệ bị bọc)
     */
    protected DomainException(HttpStatus status, String code, String message, Throwable cause) {
        // Gọi constructor RuntimeException để lưu cả thông điệp và cause.
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    /** @return mã trạng thái HTTP tương ứng với ngoại lệ */
    public HttpStatus getStatus() { return status; }

    /** @return mã lỗi ổn định theo máy */
    public String getCode() { return code; }
}
