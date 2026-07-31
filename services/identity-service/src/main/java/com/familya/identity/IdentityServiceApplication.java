package com.familya.identity;

import com.familya.identity.application.usecase.RegisterUserUseCase;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.Instant;

/**
 * Điểm khởi động (entry point) của microservice {@code identity-service}.
 *
 * <p>Service chịu trách nhiệm quản lý vòng đời người dùng trong hệ thống
 * {@code Familya}, bao gồm: đăng ký, xác thực, quản lý phiên đăng nhập,
 * xác minh email, khóa tài khoản và cập nhật thông tin danh tính. Service
 * tuân thủ kiến trúc hexagonal (ports & adapters), được khởi chạy bằng
 * Spring Boot và tích hợp các {@code platform starter} dùng chung của
 * nền tảng:
 * <ul>
 *     <li>{@code platform-spring-boot-starter} – cấu hình Spring Boot chuẩn.</li>
 *     <li>{@code platform-outbox-starter} – hỗ trợ Outbox pattern cho sự kiện.</li>
 *     <li>{@code platform-security-starter} – bảo mật và cấp phát token.</li>
 *     <li>{@code platform-observability-starter} – logging, metrics, tracing.</li>
 *     <li>{@code platform-resilience-starter} – retry, circuit-breaker,…</li>
 *     <li>{@code platform-grpc-starter} – máy chủ gRPC.</li>
 * </ul>
 *
 * <p>Lớp này kích hoạt quét thành phần (component scan) cho cả package
 * {@code com.familya.identity} (mã nguồn riêng của service) lẫn
 * {@code com.familya.platform} (các bean dùng chung của nền tảng) đồng
 * thời bật tính năng {@link EnableScheduling scheduling} cho các tác vụ
 * chạy nền (ví dụ: dọn dẹp token hết hạn, khóa tài khoản theo lịch,…).
 *
 * @author Familya Platform Team
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePackages = {
        "com.familya.identity",
        "com.familya.platform"
})
@EnableScheduling
public class IdentityServiceApplication {

    /**
     * Hàm main chuẩn của ứng dụng Spring Boot.
     *
     <p>Quy trình xử lý:
     * <ol>
     *     <li>Tiếp nhận tham số dòng lệnh {@code args} (cổng, profile,…).</li>
     *     <li>Ủy quyền cho {@link SpringApplication#run(Class, String...)}
     *         khởi tạo {@code ApplicationContext}, nạp các {@code @Configuration},
     *         {@code @Component}, thực thi {@code CommandLineRunner}…</li>
     *     <li>Đăng ký JVM shutdown hook để đóng {@code DataSource},
     *         giải phóng tài nguyên gRPC, Kafka listener một cách an toàn.</li>
     * </ol>
     *
     * @param args các tham số dòng lệnh truyền cho Spring Boot
     *             (ví dụ: {@code --server.port=8080}).
     */
    public static void main(String[] args) {
        SpringApplication.run(IdentityServiceApplication.class, args);
    }

    /**
     * Cung cấp bean {@link RegisterUserUseCase.Clock} dùng trong tầng
     * use case để lấy thời điểm hiện tại.
     *
     * <p>Việc trừu tượng hóa đồng hồ giúp:
     * <ul>
     *     <li>Dễ dàng viết unit test với thời gian giả lập (fake clock).</li>
     *     <li>Giữ tính nhất quán giữa các use case trong service khi cần
     *         đóng dấu thời gian cho sự kiện domain.</li>
     * </ul>
     *
     * @return một {@link RegisterUserUseCase.Clock} trả về {@link Instant#now()}.
     */
    @Bean
    public RegisterUserUseCase.Clock clock() {
        return Instant::now;
    }

    /**
     * Cung cấp bean {@link java.time.Clock} chuẩn của {@code java.time}
     * với múi giờ UTC để các thành phần khác (ví dụ: bộ lập lịch,
     * validator,… sử dụng chung).
     *
     * <p>UTC được chọn làm múi giờ tham chiếu nhằm tránh sai lệch khi
     * service chạy trong các container có múi giờ khác nhau.
     *
     * @return đối tượng {@link java.time.Clock} hệ thống theo UTC.
     */
    @Bean
    public java.time.Clock systemClock() {
        return java.time.Clock.systemUTC();
    }
}
