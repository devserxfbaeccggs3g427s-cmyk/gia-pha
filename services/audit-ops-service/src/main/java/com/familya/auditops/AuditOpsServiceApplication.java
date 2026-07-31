/**
 * Lớp khởi động (entry point) của dịch vụ Audit & Operations.
 * <p>
 * Dịch vụ này chịu trách nhiệm quản lý ba khối chức năng chính trong kiến trúc hệ thống:
 * </p>
 * <ul>
 *   <li>Endpoint công khai {@code /api/v2/operations} phục vụ hai mục đích:
 *       đọc trạng thái operation (polling) và đăng ký (register) operation mới.</li>
 *   <li>Cung cấp máy trạng thái Saga chia sẻ và các nguyên thuỷ (primitives)
 *       điều phối (orchestrator) dùng chung cho toàn bộ bounded context.</li>
 *   <li>Sở hữu nhật ký kiểm toán (audit log) chỉ-append và bề mặt
 *       thao tác dành cho operator (retry, cancel, xem lịch sử).</li>
 * </ul>
 *
 * <p>Theo quyết định kiến trúc ADR-003, dịch vụ này <b>KHÔNG ĐƯỢC</b> nắm
 * quyền hạn về nghiệp vụ (business authority) hay phân quyền (authorization)
 * của các bounded context khác. Nó chỉ đóng vai trò chiếu (projection)
 * phục vụ giám sát, audit và thao tác vận hành.</p>
 *
 * @author Gia phả team
 * @since 1.0.0
 */
package com.familya.auditops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@SpringBootApplication(scanBasePackages = {
        "com.familya.auditops",
        "com.familya.platform"
})
@EnableScheduling
public class AuditOpsServiceApplication {

    /**
     * Hàm main khởi động ứng dụng Spring Boot.
     * <p>
     * Quy trình khởi động bao gồm:
     * </p>
     * <ol>
     *   <li>Khởi tạo ApplicationContext của Spring.</li>
     *   <li>Quét (scan) các bean trong hai package {@code com.familya.auditops}
     *       và {@code com.familya.platform} để đăng ký các thành phần nghiệp vụ
     *       và các starter dùng chung của platform.</li>
     *   <li>Kích hoạt cơ chế scheduling ({@link EnableScheduling}) cho các
     *       tác vụ nền như relay outbox, health check định kỳ, v.v.</li>
     *   <li>Lắng nghe các cổng (port) HTTP/gRPC đã cấu hình.</li>
     * </ol>
     *
     * @param args các tham số dòng lệnh được truyền cho JVM
     */
    public static void main(String[] args) {
        SpringApplication.run(AuditOpsServiceApplication.class, args);
    }

    /**
     * Cung cấp {@link Clock} hệ thống theo múi giờ UTC.
     * <p>
     * Bean này được inject vào các usecase để có thể giả lập (mock) thời
     * gian trong kiểm thử đơn vị mà không cần thay đổi {@code System.currentTimeMillis()}.
     * Sử dụng UTC để đảm bảo tính nhất quán giữa các múi giờ khi log, audit
     * hoặc tính toán deadline cho Saga.
     * </p>
     *
     * @return đối tượng {@link Clock} mặc định của hệ thống theo UTC
     */
    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}