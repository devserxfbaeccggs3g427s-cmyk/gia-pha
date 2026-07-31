package com.familya.transfer;

/**
 * Lớp khởi động (entry point) của microservice <b>transfer-service</b>.
 *
 * <p>Đây là service chịu trách nhiệm quản lý các thao tác <i>chuyển nhượng</i>
 * (transfer) liên quan đến <b>cây gia phả</b> trong hệ thống Familya —
 * ví dụ: chuyển quyền sở hữu gia phả, chuyển giao quyền quản trị, hoặc các
 * thao tác di chuyển dữ liệu gia phả giữa các tài khoản người dùng.</p>
 *
 * <h3>Vai trò trong kiến trúc hệ thống</h3>
 * <ul>
 *   <li>Được đăng ký như một microservice độc lập, giao tiếp với các service
 *       khác thông qua cơ chế bất đồng bộ (event-based) hoặc HTTP thông qua
 *       API Gateway.</li>
 *   <li>Tuân thủ kiến trúc phân lớp (layered architecture) bao gồm:
 *       <ul>
 *           <li><b>domain</b> — chứa các entity, value object và logic nghiệp vụ cốt lõi.</li>
 *           <li><b>application</b> — chứa các use case điều phối luồng xử lý.</li>
 *           <li><b>adapter</b> (in/web, out/persistence) — chứa các thành phần
 *               giao tiếp với thế giới bên ngoài (REST, JPA, Message Broker…).</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <h3>Các annotation cấu hình</h3>
 * <ul>
 *   <li>{@link SpringBootApplication} — đánh dấu đây là ứng dụng Spring Boot,
 *       tự động bật cấu hình tự động (<i>auto-configuration</i>) và quét
 *       component trong các package được chỉ định.</li>
 *   <li>{@link EnableScheduling} — kích hoạt cơ chế lập lịch tác vụ
 *       (<code>@Scheduled</code>) phục vụ cho các nghiệp vụ chạy nền của
 *       transfer-service (ví dụ: dọn dẹp các yêu cầu chuyển nhượng hết hạn,
 *       đồng bộ trạng thái transfer, v.v.).</li>
 * </ul>
 *
 * <p>Lưu ý: Package gốc được quét gồm <code>com.familya.transfer</code>
 * (mã nguồn của service này) và <code>com.familya.platform</code>
 * (các thành phần dùng chung trong hệ sinh thái Familya như logging,
 * security, common configuration…), đảm bảo các bean dùng chung được
 * nạp khi service khởi động.</p>
 *
 * @author  Đội phát triển Familya
 * @since   1.0.0
 */
@SpringBootApplication(scanBasePackages = { "com.familya.transfer", "com.familya.platform" })
@EnableScheduling
public class Application {

    /**
     * Hàm main — điểm vào chuẩn của mọi ứng dụng Java.
     *
     * <p>Khi service được khởi chạy (qua lệnh <code>java -jar</code>,
     * <code>mvn spring-boot:run</code>, hoặc bởi Docker/Kubernetes),
     * JVM sẽ gọi hàm này đầu tiên. Bên trong, phương thức
     * {@link SpringApplication#run(Class, String...)} thực hiện chuỗi
     * công việc sau:</p>
     *
     * <ol>
     *   <li><b>Tạo ApplicationContext</b> — khởi tạo Spring IoC container
     *       chứa toàn bộ bean của ứng dụng.</li>
     *   <li><b>Nạp cấu hình</b> — đọc <code>application.yml</code> và các
     *       thuộc tính môi trường (biến môi trường, tham số dòng lệnh).</li>
     *   <li><b>Quét component</b> — phát hiện và đăng ký các {@code @Component},
     *       {@code @Service}, {@code @Repository}, {@code @Controller}… trong
     *       các package đã khai báo ở {@link SpringBootApplication#scanBasePackages()}.</li>
     *   <li><b>Khởi tạo embedded server</b> — nếu là web service, container
     *       Tomcat (mặc định) sẽ được khởi động để lắng nghe các request HTTP.</li>
     *   <li><b>Chạy ApplicationRunner / CommandLineRunner</b> — thực thi các
     *       tác vụ khởi động tuỳ biến (nếu có).</li>
     * </ol>
     *
     * @param args Mảng tham số dòng lệnh được truyền vào khi chạy JVM.
     *             Các đối số này được Spring chuyển tiếp tới
     *             {@code SpringApplication} và có thể dùng để ghi đè
     *             thuộc tính cấu hình, ví dụ
     *             <code>--server.port=8081</code>,
     *             <code>--spring.profiles.active=prod</code>.
     */
    public static void main(String[] args) {
        // Ủy quyền toàn bộ quá trình bootstrap cho Spring Boot.
        // Tham số đầu tiên là class cấu hình chính (cùng class này),
        // tham số thứ hai là mảng đối số dòng lệnh.
        SpringApplication.run(Application.class, args);
    }
}
