package com.familya.identity.application.port.in;

import java.util.UUID;

/**
 * Command cho use case {@code RegisterUserUseCase}.
 *
 * <p>Command thực hiện validate sớm ở constructor compact để ngăn chặn
 * những dữ liệu rỗng/không hợp lệ đến tầng application. Đây là kỹ thuật
 * "fail fast" giúp hệ thống phản hồi nhanh và tránh những lỗi khó
 * truy vết ở tầng sâu hơn.
 *
 * <p>Để giữ command không phụ thuộc vào framework, các annotation
 * Bean Validation (ví dụ {@code @Email}, {@code @Size}) được áp dụng
 * ở controller – command chỉ chứa kiểm tra tối thiểu bằng các điều
 * kiện Java thuần.
 *
 * @param email                địa chỉ email người dùng (đã được validate ở controller).
 * @param password             mật khẩu thô (sẽ được băm bởi use case).
 * @param ipAddress            địa chỉ IP client (phục vụ rate limit).
 * @param verificationRequired cờ cho biết người dùng có cần xác minh email hay không.
 * @throws IllegalArgumentException nếu email hoặc password không thỏa mãn ràng buộc tối thiểu.
 */
public record RegisterUserCommand(
        String email,
        String password,
        String ipAddress,
        boolean verificationRequired
) {
    /**
     * Constructor compact – thực hiện validate tối thiểu.
     *
     * <p>Quy tắc kiểm tra:
     * <ul>
     *     <li>{@code email} không được {@code null} hoặc rỗng (sau khi
     *         trim dấu cách).</li>
     *     <li>{@code password} phải có độ dài tối thiểu 12 ký tự –
     *         đây là chính sách bảo mật tối thiểu của hệ thống.</li>
     * </ul>
     */
    public RegisterUserCommand {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email is required");
        }
        if (password == null || password.length() < 12) {
            throw new IllegalArgumentException("password must be at least 12 characters");
        }
    }

    /**
     * Sinh UUID ngẫu nhiên đại diện cho {@code userId} sẽ được tạo.
     *
     * <p>Phương thức này được tách riêng (thay vì thực hiện ngay trong
     * constructor) để:
     * <ul>
     *     <li>Giữ command là bất biến – không thay đổi giá trị sau
     *         khi tạo.</li>
     *     <li>Cho phép unit test kiểm soát giá trị userId thông qua
     *         mock UUID generator.</li>
     * </ul>
     *
     * @return UUID ngẫu nhiên.
     */
    public UUID userId() { return UUID.randomUUID(); }
}
