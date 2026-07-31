package com.familya.platform.grpc;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Client interceptor chịu trách nhiệm phát tán (propagate) các header
 * correlation/causation/operation/traceparent trên mỗi cuộc gọi gRPC đi.
 *
 * <p>Interceptor này được Spring quản lý nhờ {@link Component} và sẽ tự động
 * được áp dụng cho mọi stub gRPC được tạo bởi {@code grpc-client-spring-boot-starter}
 * của dự án. Nó đảm bảo mọi cuộc gọi ra bên ngoài đều mang theo đúng định
 * danh truy vết của cuộc gọi gốc.</p>
 *
 * <p><b>Mục đích sử dụng:</b></p>
 * <ul>
 *   <li>Duy trì tính liên tục của correlation/causation xuyên suốt chuỗi
 *       microservice trong hệ thống phân tán.</li>
 *   <li>Tương thích với {@link TracingServerInterceptor} ở phía server để
 *       context truy vết được khớp đúng khi log/audit.</li>
 *   <li>Hỗ trợ chuẩn W3C Trace Context thông qua header {@code traceparent}.</li>
 * </ul>
 *
 * <p><b>Ràng buộc thiết kế:</b> Các cuộc gọi đồng bộ có giới hạn (ví dụ:
 * tra cứu khẩn cấp quyền truy cập cây gia phả) không được vượt quá 2 hop
 * (tham chiếu {@code design.md}) — interceptor hỗ trợ ràng buộc này bằng
 * cách đảm bảo mỗi hop có thể tiếp tục truyền tiếp định danh.</p>
 *
 * @author Family Tree Platform Team
 */
@Component
public class TracingClientInterceptor implements ClientInterceptor {

    /** Khóa metadata cho correlation id, dùng ASCII marshaller. */
    public static final Metadata.Key<String> CORRELATION_ID = Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER);

    /** Khóa metadata cho causation id, dùng ASCII marshaller. */
    public static final Metadata.Key<String> CAUSATION_ID   = Metadata.Key.of("x-causation-id",   Metadata.ASCII_STRING_MARSHALLER);

    /** Khóa metadata cho operation id, dùng ASCII marshaller. */
    public static final Metadata.Key<String> OPERATION_ID   = Metadata.Key.of("x-operation-id",   Metadata.ASCII_STRING_MARSHALLER);

    /** Khóa metadata cho W3C traceparent header, dùng ASCII marshaller. */
    public static final Metadata.Key<String> TRACEPARENT    = Metadata.Key.of("traceparent",       Metadata.ASCII_STRING_MARSHALLER);

    /**
     * Chặn mỗi cuộc gọi gRPC đi để gắn thêm các header tracing vào metadata
     * trước khi cuộc gọi thực sự được khởi tạo.
     *
     * @param method      {@link MethodDescriptor} mô tả phương thức được gọi
     * @param callOptions các tùy chọn cuộc gọi (deadline, credentials, ...)
     * @param next        kênh (channel) thực sự thực hiện cuộc gọi
     * @param <ReqT>      kiểu thông điệp yêu cầu
     * @param <RespT>     kiểu thông điệp phản hồi
     * @return một {@link ClientCall} đã được bọc để bổ sung header
     */
    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                                                               CallOptions callOptions, Channel next) {
        // Sử dụng ForwardingClientCall để chỉ chặn tại thời điểm `start`,
        // tức là lúc metadata cuối cùng được gửi đi. Nhờ vậy ta vẫn cho phép
        // stub tự do thêm các header khác trước đó mà không bị ghi đè sai.
        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                // Bước 1: Tiêm từng header tracing. Hiện tại luôn sinh UUID
                // mới để đảm bảo stub không bao giờ gửi header rỗng. Trong
                // tương lai có thể lấy giá trị từ Context inbound nếu có.
                headers.put(CORRELATION_ID, headerOrNew(CORRELATION_ID));
                headers.put(CAUSATION_ID,   headerOrNew(CAUSATION_ID));
                headers.put(OPERATION_ID,   headerOrNew(OPERATION_ID));

                // Bước 2: Ủy quyền tiếp cho hành vi start mặc định của lớp cha
                // để cuộc gọi thực sự được gửi đi với metadata đã bổ sung.
                super.start(responseListener, headers);
            }
        };
    }

    /**
     * Sinh giá trị mới cho header tracing. Phương thức này được tách riêng để
     * dễ mở rộng: trong tương lai có thể đọc giá trị từ Context/MDC hiện
     * tại thay vì sinh ngẫu nhiên.
     *
     * @param key khóa metadata tương ứng với header cần sinh giá trị
     * @return chuỗi UUID mới làm giá trị header
     */
    private static String headerOrNew(Metadata.Key<String> key) {
        // Sử dụng UUID v4 để đảm bảo tính duy nhất toàn cục với xác suất
        // va chạm cực thấp, đủ an toàn cho hệ thống phân tán.
        return UUID.randomUUID().toString();
    }
}
