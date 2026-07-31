package com.familya.media;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Điểm khởi động (entrypoint) của microservice <b>media-service</b>.
 *
 * <p>Service này chịu trách nhiệm quản lý vòng đời của tài sản media (ảnh, video, audio,
 * tài liệu) trong hệ thống gia phả, bao gồm: tạo album, quản lý upload có ký capability,
 * scan chống mã độc, tạo thumbnail, gắn kết media với thành viên, xóa mềm (tombstone),
 * khôi phục và sao lưu binary.</p>
 *
 * <p>Annotation {@link SpringBootApplication} quét cả package {@code com.familya.media}
 * (chứa code riêng của service) lẫn {@code com.familya.platform} (chứa các thành phần
 * nền tảng chia sẻ như outbox, telemetry, security filter) để Spring tự động đăng ký bean.</p>
 *
 * <p>{@link EnableScheduling} kích hoạt cơ chế lập lịch của Spring, cho phép các tác vụ
 * nền như {@code MediaCleanupScheduler} (dọn dẹp tombstone quá hạn, dọn dẹp tham chiếu
 * mồ côi) chạy tự động theo cron định sẵn.</p>
 *
 * @author gia-pha team
 */
@SpringBootApplication(scanBasePackages = { "com.familya.media", "com.familya.platform" })
@EnableScheduling
public class Application {
    /**
     * Hàm main — được JVM gọi khi khởi động service.
     *
     <p>Ủy quyền toàn bộ quá trình bootstrap cho {@link SpringApplication#run(Class, String...)},
     * bao gồm: tạo {@code ApplicationContext}, nạp cấu hình từ {@code application.yml},
     * kích hoạt auto-configuration, quét component và khởi động embedded HTTP server.</p>
     *
     * @param args tham số dòng lệnh (profile, debug, v.v.) — được Spring Boot xử lý tự động
     */
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
