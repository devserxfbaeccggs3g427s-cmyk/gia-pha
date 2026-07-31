package com.familya.migration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Lớp khởi động (entry point) của microservice {@code migration-service}.
 *
 * <p>Service này chịu trách nhiệm quản lý và vận hành các tác vụ di chuyển dữ liệu
 * (data migration) trong hệ thống gia phả. Đây là microservice thuộc hệ sinh thái
 * {@code Family Tree}, được xây dựng trên nền tảng {@code familya-platform}
 * nhằm đảm bảo tính nhất quán về cấu hình, bảo mật, khả năng quan sát
 * (observability) và độ bền (resilience) giữa các service trong cùng hệ thống.</p>
 *
 * <h2>Các thành phần chính được kích hoạt</h2>
 * <ul>
 *   <li>{@link SpringBootApplication}: kích hoạt cơ chế auto-configuration của
 *       Spring Boot, cho phép ứng dụng tự động cấu hình dựa trên các dependency
 *       có trong classpath (ví dụ: JDBC, Flyway, Kafka, gRPC...).</li>
 *   <li>{@code scanBasePackages}: mở rộng phạm vi quét bean ngoài package mặc định
 *       của lớp này để bao gồm cả package {@code com.familya.platform}. Điều này
 *       cho phép các thành phần dùng chung của nền tảng (platform starters) như
 *       security, outbox, observability... được Spring nhận diện và đăng ký
 *       như những bean quản lý được.</li>
 *   <li>{@link EnableScheduling}: kích hoạt bộ lập lịch (scheduler) của Spring,
 *       cho phép service thực thi các tác vụ định kỳ thông qua annotation
 *       {@code @Scheduled} — thường dùng cho các job migration chạy nền.</li>
 * </ul>
 *
 * <h2>Quy ước và tiêu chuẩn áp dụng</h2>
 * <ul>
 *   <li>Tuân thủ các ADR (Architecture Decision Record) của dự án gia phả,
 *       đặc biệt ADR-002 (phân tách service) và ADR-009 (chiến lược migration).</li>
 *   <li>Không chứa logic nghiệp vụ; toàn bộ nghiệp vụ được đặt trong các
 *       module con (domain, application, infrastructure) theo kiến trúc
 *       hexagonal / clean architecture.</li>
 * </ul>
 *
 * @author Family Tree Platform Team
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePackages = { "com.familya.migration", "com.familya.platform" })
@EnableScheduling
public class Application {

    /**
     * Phương thức {@code main} — điểm vào chuẩn của một ứng dụng Java.
     *
     * <p>Khi JVM khởi chạy service, phương thức này sẽ:</p>
     * <ol>
     *   <li>Được JVM gọi đầu tiên với các đối số dòng lệnh được truyền vào
     *       (ví dụ: {@code --server.port=8081}, profile Spring, biến môi trường...).</li>
     *   <li>Ủy quyền cho {@link SpringApplication#run(Class, String...)} để:
     *       <ul>
     *         <li>Tạo {@code ApplicationContext} (vùng chứa bean của Spring).</li>
     *         <li>Thực hiện quá trình auto-configuration dựa trên classpath
     *             và file {@code application.yml}.</li>
     *         <li>Kích hoạt cơ chế quét component để đăng ký các bean được
     *             khai báo trong package {@code com.familya.migration} và
     *             {@code com.familya.platform}.</li>
     *         <li>Bật scheduler (do {@link EnableScheduling} ở cấp class).</li>
     *         <li>Khởi chạy máy chủ nhúng (embedded server) nếu service được
     *             cấu hình là web service; ngược lại chạy như tác vụ nền.</li>
     *       </ul>
     *   </li>
     * </ol>
     *
     * @param args mảng tham số dòng lệnh được JVM truyền cho chương trình.
     *             Có thể bao gồm các đối số chuẩn của Spring Boot
     *             (ví dụ: {@code --spring.profiles.active=prod}),
     *             cũng như các đối số tùy chỉnh của ứng dụng.
     */
    public static void main(String[] args) {
        // Ủy quyền hoàn toàn cho Spring Boot: tạo ApplicationContext,
        // áp dụng auto-configuration, nạp cấu hình từ application.yml,
        // và khởi động toàn bộ vòng đời của microservice migration-service.
        SpringApplication.run(Application.class, args);
    }
}
