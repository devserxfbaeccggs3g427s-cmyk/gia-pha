package com.familya.platform.grpc;

import io.grpc.ServerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Cấu hình máy chủ gRPC mặc định cho các dịch vụ trong nền tảng Family Tree.
 *
 * <p>Lớp này chỉ được kích hoạt khi thuộc tính {@code familya.grpc.server.enabled=true}
 * được cấu hình. Việc dùng {@link ConditionalOnProperty} cho phép mỗi dịch vụ
 * tự bật/tắt máy chủ gRPC theo nhu cầu nghiệp vụ mà không ảnh hưởng đến các
 * dịch vụ khác.</p>
 *
 * <p><b>Các giá trị mặc định được thiết lập:</b></p>
 * <ul>
 *   <li>Cổng lắng nghe (mặc định {@code 9090}), cấu hình qua
 *       {@code familya.grpc.server.port}.</li>
 *   <li>Thread pool cố định 8 luồng cho executor.</li>
 *   <li>Keep-alive time 30 giây, timeout 5 giây.</li>
 *   <li>Cho phép giữ kết nối khi không có cuộc gọi.</li>
 *   <li>Đóng kết nối nhàn rỗi sau 60 giây để giải phóng tài nguyên.</li>
 * </ul>
 *
 * <p><b>Quy tắc deadline:</b> Việc truyền deadline được thực thi bởi Netty server
 * bên dưới. Bất kỳ client stub nào bỏ qua deadline sẽ khiến circuit breaker
 * ngắt mạch, đảm bảo cuộc gọi không bao giờ treo vô hạn. Mỗi dịch vụ đăng
 * ký các {@code BindableService} của mình bằng cách cung cấp chúng làm tham
 * số cho bean {@code grpcServer}.</p>
 *
 * @author Family Tree Platform Team
 */
@Configuration
@ConditionalOnProperty(prefix = "familya.grpc.server", name = "enabled", havingValue = "true", matchIfMissing = false)
public class GrpcServerConfig {

    /**
     * Cổng mà máy chủ gRPC sẽ lắng nghe. Mặc định là {@code 9090} và có thể
     * bị override qua thuộc tính {@code familya.grpc.server.port}.
     */
    @Value("${familya.grpc.server.port:9090}")
    private int port;

    /**
     * Khởi tạo và khởi động máy chủ gRPC với các {@code BindableService} được
     * cung cấp bởi ứng dụng.
     *
     * <p>Bean này được đánh dấu {@link Primary} để tránh xung đột khi có nhiều
     * bean {@code io.grpc.Server} trong context. Khi context bị đóng,
     * {@code shutdownNow} sẽ được Spring gọi để dừng máy chủ ngay lập tức.</p>
     *
     * @param services danh sách {@link io.grpc.BindableService} do ứng dụng đăng ký
     * @return {@link io.grpc.Server} đã được khởi động
     * @throws IOException nếu không thể mở cổng lắng nghe
     */
    @Bean(destroyMethod = "shutdownNow")
    @Primary
    public io.grpc.Server grpcServer(io.grpc.BindableService... services) throws IOException {
        // Bước 1: Tạo ServerBuilder cho cổng đã cấu hình, đồng thời thiết lập
        // executor với thread pool cố định. Việc dùng pool cố định giúp giới
        // hạn mức sử dụng tài nguyên và tránh tình trạng cạn kiệt luồng khi
        // lượng truy cập tăng đột biến.
        ServerBuilder<?> builder = ServerBuilder.forPort(port)
                .executor(Executors.newFixedThreadPool(8))
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(5, TimeUnit.SECONDS)
                .permitKeepAliveWithoutCalls(true)
                .maxConnectionIdle(60, TimeUnit.SECONDS);

        // Bước 2: Đăng ký từng service do ứng dụng cung cấp. Mỗi service sẽ
        // được gRPC server định tuyến tới dựa trên tên đầy đủ của phương thức.
        for (io.grpc.BindableService svc : services) {
            builder.addService(svc);
        }

        // Bước 3: Build và start server ngay lập tức.
        io.grpc.Server server = builder.build().start();

        // Bước 4: Đăng ký shutdown hook để đảm bảo server được đóng gọn gàng
        // khi JVM tắt (ví dụ: khi triển khai rolling update).
        Runtime.getRuntime().addShutdownHook(new Thread(server::shutdownNow, "grpc-shutdown"));

        // Bước 5: Trả về server đang chạy để Spring quản lý vòng đời.
        return server;
    }
}
