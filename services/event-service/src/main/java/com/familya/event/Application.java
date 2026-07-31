package com.familya.event;

import com.familya.event.application.usecase.CreateDomainEventUseCase;
import com.familya.event.application.usecase.UpdateDomainEventUseCase;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.Instant;

/**
 * Điểm vào (entry point) của microservice <b>event-service</b>.
 *
 * <h2>Trách nhiệm</h2>
 * <ul>
 *   <li>Khởi động Spring Boot với scan gói {@code com.familya.event}
 *       và {@code com.familya.platform} (để nạp các bean dùng chung).</li>
 *   <li>Bật {@code @EnableScheduling} — cho phép các tác vụ theo lịch
 *       (ví dụ: relay outbox, sweep job) chạy.</li>
 *   <li>Khai báo các {@code @Bean} cốt lõi:
 *     <ul>
 *       <li>{@link CreateDomainEventUseCase.Clock} — SPI đồng hồ cho
 *           use case tạo mới.</li>
 *       <li>{@link UpdateDomainEventUseCase.Clock} — SPI đồng hồ cho
 *           use case cập nhật.</li>
 *       <li>{@link Clock} — đồng hồ UTC hệ thống dùng chung.</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h2>Cấu hình</h2>
 * <p>Xem {@code src/main/resources/application.yml} cho các thiết lập:
 * DataSource, Flyway, Outbox relay, Management endpoints.
 *
 * @author gia-pha platform
 */
@SpringBootApplication(scanBasePackages = { "com.familya.event", "com.familya.platform" })
@EnableScheduling
public class Application {

    /**
     * Hàm main — chạy ứng dụng Spring Boot.
     *
     * @param args tham số dòng lệnh (chuẩn của Spring Boot).
     */
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    /**
     * Bean {@link CreateDomainEventUseCase.Clock} dùng {@link Instant#now()}
     * làm nguồn thời gian cho use case tạo sự kiện. Có thể được override
     * bởi test (ví dụ cố định thời gian trong bài kiểm thử).
     *
     * @return SPI đồng hồ mặc định.
     */
    @Bean public CreateDomainEventUseCase.Clock createClock() { return Instant::now; }

    /**
     * Bean {@link UpdateDomainEventUseCase.Clock} tương tự cho use case
     * cập nhật. Tách riêng để linh hoạt trong test từng use case.
     *
     * @return SPI đồng hồ mặc định.
     */
    @Bean public UpdateDomainEventUseCase.Clock updateClock() { return Instant::now; }

    /**
     * Bean {@link Clock} UTC dùng cho các thành phần khác (ví dụ
     * dispatchJob) không thuộc use case cụ thể.
     *
     * @return {@link Clock#systemUTC()}.
     */
    @Bean public Clock systemClock() { return Clock.systemUTC(); }
}
