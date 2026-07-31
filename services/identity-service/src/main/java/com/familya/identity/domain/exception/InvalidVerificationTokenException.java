package com.familya.identity.domain.exception;

/**
 * Ngoại lệ được ném khi token xác minh email không hợp lệ.
 *
 * <p>Lý do có thể bao gồm:
 * <ul>
 *     <li>Token không tồn tại trong cơ sở dữ liệu.</li>
 *     <li>Token không thuộc về người dùng cần xác minh.</li>
 *     <li>Token đã được sử dụng.</li>
 *     <li>Token đã hết hạn.</li>
 * </ul>
 *
 * <p>Lớp này kế thừa trực tiếp {@link RuntimeException} thay vì
 * {@code DomainException} vì thường được ánh xạ thành {@code 400 Bad
 * Request} hoặc {@code 404 Not Found} tùy ngữ cảnh, và để tránh phụ
 * thuộc vào {@code familya.platform.error}.
 *
 * @author Familya Platform Team
 * @since 1.0.0
 */
public class InvalidVerificationTokenException extends RuntimeException {
    /**
     * Khởi tạo ngoại lệ với thông điệp mô tả.
     *
     * @param message mô tả lý do token không hợp lệ.
     */
    public InvalidVerificationTokenException(String message) {
        super(message);
    }
}
