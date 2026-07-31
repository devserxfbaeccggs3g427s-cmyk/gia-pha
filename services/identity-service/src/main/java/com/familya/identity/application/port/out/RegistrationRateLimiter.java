package com.familya.identity.application.port.out;

import java.time.Instant;

/**
 * Port (cổng) ra dùng để giới hạn số lượt đăng ký theo địa chỉ IP trong
 * một khoảng thời gian.
 *
 * <p>Mục đích:
 * <ul>
 *     <li>Chống spam tạo tài khoản hàng loạt.</li>
 *     <li>Giảm thiểu rủi ro brute-force khi kẻ tấn công cố gắng dò
 *         mật khẩu thông qua nhiều tài khoản khác nhau.</li>
 *     <li>Bảo vệ tài nguyên hệ thống (DB, email service) khỏi bị
 *         lạm dụng.</li>
 * </ul>
 *
 * <p>Triển khai mặc định trong service này là
 * {@link com.familya.identity.adapter.out.persistence.JdbcRegistrationRateLimiter}
 * sử dụng cơ chế bucket theo giờ trong cơ sở dữ liệu.
 *
 * @author Familya Platform Team
 * @since 1.0.0
 */
public interface RegistrationRateLimiter {
    /**
     * Thử ghi nhận một lượt đăng ký cho IP tại thời điểm nhất định.
     *
     * <p>Triển khai sẽ kiểm tra quota hiện tại; nếu vượt ngưỡng sẽ
     * trả về {@code false} mà không ghi thêm bản ghi.
     *
     * @param ipAddress địa chỉ IP của client.
     * @param now       thời điểm hiện tại (giúp test dễ dàng với
     *                  đồng hồ giả lập).
     * @return {@code true} nếu còn dư quota, {@code false} nếu đã vượt ngưỡng.
     */
    boolean tryRegister(String ipAddress, Instant now);
}
