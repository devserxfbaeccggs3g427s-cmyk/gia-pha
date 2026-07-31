package com.familya.sharing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Điểm vào (entry point) của sharing-service.
 * <p>
 * Class này khởi động ứng dụng Spring Boot với các cấu hình mặc định của
 * module, đồng thời kích hoạt cơ chế {@link EnableScheduling scheduling} để
 * các tác vụ nền (như relay outbox &rarr; Kafka, replay ledger, v.v.) có thể
 * được thực thi theo lịch.
 * <p>
 * Quét bean trong hai package gốc:
 * <ul>
 *     <li>{@code com.familya.sharing} &mdash; các bean của sharing-service.</li>
 *     <li>{@code com.familya.platform} &mdash; các bean tiện ích do platform
 *         cung cấp (security, observability, outbox, gRPC, resilience...).</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = { "com.familya.sharing", "com.familya.platform" })
@EnableScheduling
public class Application {
    /**
     * Phương thức main &mdash; khởi động Spring Boot và toàn bộ context.
     *
     * @param args tham số dòng lệnh (cổng, profile, cấu hình...).
     */
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}