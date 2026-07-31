package com.familya.identity.adapter.out.persistence;

import com.familya.identity.application.port.out.RegistrationRateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Adapter triển khai {@link RegistrationRateLimiter} bằng JDBC, sử dụng
 * cơ chế "bucket theo giờ".
 *
 * <p>Ý tưởng chính:
 * <ul>
 *     <li>Mỗi địa chỉ IP được tính trong một "bucket" (khoảng thời gian
 *         1 giờ). Bucket được xác định bằng cách cắt {@code Instant}
 *         về đầu giờ ({@link ChronoUnit#HOURS}).</li>
 *     <li>Trước khi cho phép đăng ký, kiểm tra số lượng bản ghi trong
 *         bảng {@code registration_attempt} thuộc bucket hiện tại.
 *         Nếu vượt ngưỡng {@code limitPerHour} thì từ chối.</li>
 *     <li>Nếu còn dư quota, ghi nhận thêm một bản ghi và trả về
 *         {@code true} (cho phép).</li>
 * </ul>
 *
 * <p>Cơ chế này chống được tình trạng một IP tạo hàng loạt tài khoản
 * trong thời gian ngắn (spam, brute-force tạo user,…) bằng cách giới
 * hạn số lượng đăng ký tối đa trong một giờ.
 *
 * @author Familya Platform Team
 * @since 1.0.0
 */
@Component
public class JdbcRegistrationRateLimiter implements RegistrationRateLimiter {

    /** Template JDBC dùng chung. */
    private final NamedParameterJdbcTemplate jdbc;
    /** Số lượng đăng ký tối đa cho phép trong một giờ cho mỗi IP. */
    private final int limitPerHour;

    /**
     * Khởi tạo limiter với cấu hình ngưỡng.
     *
     * @param jdbc         template JDBC.
     * @param limitPerHour ngưỡng cho phép (mặc định 5, cấu hình qua
     *                     {@code familya.identity.registration-per-ip-per-hour}).
     */
    public JdbcRegistrationRateLimiter(NamedParameterJdbcTemplate jdbc,
                                       @Value("${familya.identity.registration-per-ip-per-hour:5}") int limitPerHour) {
        this.jdbc = jdbc;
        this.limitPerHour = limitPerHour;
    }

    /**
     * Kiểm tra và ghi nhận một lượt đăng ký cho IP tại thời điểm {@code now}.
     *
     * <p>Quy trình xử lý:
     * <ol>
     *     <li>Tính {@code windowStart} – đầu giờ chứa {@code now}.
     *         Việc này đảm bảo tất cả các request trong cùng một giờ
     *         chia sẻ chung một bucket.</li>
     *     <li>Đếm số bản ghi trong bảng {@code registration_attempt}
     *         ứng với IP và bucket hiện tại.</li>
     *     <li>Nếu số lượng vượt ngưỡng → trả về {@code false} (từ chối).</li>
     *     <li>Nếu còn dư quota, chèn một bản ghi đăng ký mới và trả
     *         về {@code true}.</li>
     * </ol>
     *
     * @param ipAddress địa chỉ IP của client (có thể là IPv4 hoặc IPv6).
     * @param now       thời điểm hiện tại để xác định bucket.
     * @return {@code true} nếu còn dư quota để đăng ký, {@code false}
     *         nếu đã vượt ngưỡng.
     */
    @Override
    public boolean tryRegister(String ipAddress, Instant now) {
        // Bước 1: cắt về đầu giờ để mọi request trong cùng giờ chia sẻ bucket.
        Instant windowStart = now.truncatedTo(ChronoUnit.HOURS);
        // Bước 2: đếm số lượt đăng ký đã có của IP trong bucket hiện tại.
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(1) FROM registration_attempt WHERE ip_address = :ip AND bucket_hour = :b",
                new MapSqlParameterSource().addValue("ip", ipAddress).addValue("b", Timestamp.from(windowStart)),
                Integer.class);
        // Bước 3: nếu đã vượt ngưỡng -> từ chối.
        if (count != null && count >= limitPerHour) {
            return false;
        }
        // Bước 4: ghi nhận một lượt đăng ký mới để áp dụng cho các lần đếm tiếp theo.
        jdbc.update(
                "INSERT INTO registration_attempt (ip_address, bucket_hour, attempted_at) VALUES (:ip, :b, :n)",
                new MapSqlParameterSource()
                        .addValue("ip", ipAddress)
                        .addValue("b", Timestamp.from(windowStart))
                        .addValue("n", Timestamp.from(now)));
        return true;
    }
}
