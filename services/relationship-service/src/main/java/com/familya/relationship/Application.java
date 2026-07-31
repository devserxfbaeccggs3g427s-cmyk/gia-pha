package com.familya.relationship;

import com.familya.relationship.application.usecase.CreateRelationshipUseCase;
import com.familya.relationship.application.usecase.TombstoneRelationshipUseCase;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Instant;

/**
 * Điểm vào (entrypoint) của Relationship service.
 * <p>
 * Vai trò:
 * </p>
 * <ul>
 *   <li>Khởi động Spring Boot với scan các package {@code com.familya.relationship}
 *       và {@code com.familya.platform} (để nhận các auto-configuration của platform).</li>
 *   <li>Bật {@link EnableScheduling} cho phép thực thi các tác vụ theo lịch
 *       (ví dụ: {@code outbox worker} nếu có).</li>
 *   <li>Cung cấp các {@code @Bean} cho {@link java.time.Clock} và các interface
 *       {@link CreateRelationshipUseCase.Clock}/{@link TombstoneRelationshipUseCase.Clock}
 *       - mặc định sử dụng {@link Instant#now()} để dễ thay thế trong test.</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = { "com.familya.relationship", "com.familya.platform" })
@EnableScheduling
public class Application {

    /**
     * Hàm main - khởi động Spring Boot container.
     *
     * @param args các đối số dòng lệnh (chuyển cho Spring Boot)
     */
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    /**
     * Bean cung cấp đồng hồ cho {@link CreateRelationshipUseCase}.
     * <p>
     * Mặc định dùng {@link Instant#now()}; trong test có thể thay thế bằng một
     * {@code Clock} cố định để có kết quả xác định.
     * </p>
     *
     * @return implementation mặc định của {@code CreateRelationshipUseCase.Clock}
     */
    @Bean public CreateRelationshipUseCase.Clock createClock() { return Instant::now; }

    /**
     * Bean cung cấp đồng hồ cho {@link TombstoneRelationshipUseCase}.
     *
     * @return implementation mặc định của {@code TombstoneRelationshipUseCase.Clock}
     */
    @Bean public TombstoneRelationshipUseCase.Clock tombstoneClock() { return Instant::now; }

    /**
     * Bean {@link java.time.Clock} tiêu chuẩn của JDK (UTC) cho các thành phần khác.
     *
     * @return {@code Clock} hệ thống UTC
     */
    @Bean public java.time.Clock systemClock() { return java.time.Clock.systemUTC(); }
}