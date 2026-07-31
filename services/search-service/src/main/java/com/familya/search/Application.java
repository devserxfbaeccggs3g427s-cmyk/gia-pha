package com.familya.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Điểm khởi đầu (entry point) của microservice search-service.
 *
 * <p>Service này chịu trách nhiệm cung cấp các API tìm kiếm (search) và
 * gợi ý (autocomplete) cho dữ liệu phả hệ (gia phả) đã được chiếu (project)
 * từ các service khác. Service chỉ đọc (read-model only), không phát sinh
 * sự kiện liên service.</p>
 *
 * <p>Các annotation chính:</p>
 * <ul>
 *   <li>{@link SpringBootApplication} - đánh dấu đây là ứng dụng Spring Boot,
 *       quét các bean trong package {@code com.familya.search} và cả
 *       {@code com.familya.platform} để nạp các thành phần dùng chung.</li>
 *   <li>{@link EnableScheduling} - kích hoạt cơ chế lập lịch của Spring, cho
 *       phép thực thi các tác vụ định kỳ (ví dụ: rebuild projection, reconcile
 *       barrier) được khai báo bằng {@code @Scheduled} trong service.</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = { "com.familya.search", "com.familya.platform" })
@EnableScheduling
public class Application {
    /**
     * Khởi động ứng dụng Spring Boot. Phương thức này ủy quyền hoàn toàn cho
     * {@link SpringApplication#run(Class, String...)} để tạo {@code ApplicationContext},
     * nạp cấu hình, đăng ký bean và khởi động các máy chủ nhúng (nếu có).
     *
     * @param args tham số dòng lệnh tiêu chuẩn, được chuyển nguyên cho Spring.
     */
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
