package com.familya.platform.grpc;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

/**
 * Server interceptor chịu trách nhiệm trích xuất các header liên quan đến
 * truy vết (correlation, causation, operation) từ mỗi cuộc gọi gRPC đến và
 * đặt chúng vào {@link io.grpc.Context} của cuộc gọi.
 *
 * <p>Lớp này được Spring tự động nạp nhờ annotation {@code @Configuration} và
 * được đăng ký toàn cục với grpc-server nhờ {@link GrpcGlobalServerInterceptor},
 * nghĩa là mọi {@code BindableService} trong ứng dụng đều được interceptor này
 * bảo vệ mà không cần cấu hình thủ công.</p>
 *
 * <p><b>Mục đích sử dụng:</b></p>
 * <ul>
 *   <li>Đảm bảo mọi log, span và audit do downstream handler tạo ra đều gắn
 *       liền với cùng một correlation id xuyên suốt một luồng xử lý.</li>
 *   <li>Hỗ trợ khả năng truy vết nguyên nhân (causation) giữa các sự kiện
 *       trong hệ thống phân tán.</li>
 *   <li>Cho phép client đối chiếu kết quả bất đồng bộ (operation id) với
 *       yêu cầu ban đầu khi polling trạng thái.</li>
 * </ul>
 *
 * <p><b>Quy tắc xử lý:</b></p>
 * <ol>
 *   <li>Nếu client gửi kèm header tương ứng, interceptor sẽ sử dụng giá trị đó.</li>
 *   <li>Nếu header bị thiếu hoặc rỗng, hệ thống tự sinh một UUID mới để đảm
 *       bảo mỗi cuộc gọi luôn có một định danh hợp lệ phục vụ logging.</li>
 * </ol>
 *
 * @author Family Tree Platform Team
 * @see TracingClientInterceptor đối tượng interceptor phía client giúp
 *      truyền các header này đi khi gọi sang dịch vụ khác.
 */
@Configuration
@GrpcGlobalServerInterceptor
public class TracingServerInterceptor implements ServerInterceptor {

    /**
     * Key trong {@link io.grpc.Context} lưu trữ correlation id của cuộc gọi.
     * Correlation id là định danh xuyên suốt một luồng nghiệp vụ (workflow)
     * và được dùng để ghép nối log giữa nhiều dịch vụ.
     */
    public static final Context.Key<String> CORRELATION_ID = Context.key("x-correlation-id");

    /**
     * Key trong {@link io.grpc.Context} lưu trữ causation id.
     * Causation id là định danh của sự kiện/sự kiện cha trực tiếp gây ra
     * cuộc gọi hiện tại, dùng để dựng cây nguyên nhân trong truy vết.
     */
    public static final Context.Key<String> CAUSATION_ID   = Context.key("x-causation-id");

    /**
     * Key trong {@link io.grpc.Context} lưu trữ operation id.
     * Operation id là định danh của thao tác bất đồng bộ sinh ra cuộc gọi,
     * được dùng để đối chiếu khi client polling kết quả tại
     * {@code GET /api/v2/operations/{operationId}}.
     */
    public static final Context.Key<String> OPERATION_ID   = Context.key("x-operation-id");

    /**
     * Chặn cuộc gọi gRPC đến, đọc các header tracing và gắn chúng vào
     * {@link io.grpc.Context} trước khi chuyển tiếp cho handler tiếp theo.
     *
     * @param call    đối tượng {@link ServerCall} mô tả cuộc gọi hiện tại
     * @param headers metadata gửi kèm từ client (chứa correlation/causation/operation)
     * @param next    handler tiếp theo trong chuỗi interceptor
     * @param <ReqT>  kiểu thông điệp yêu cầu
     * @param <RespT> kiểu thông điệp phản hồi
     * @return listener mà server sử dụng để nhận sự kiện từ client
     */
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                 Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next) {
        // Bước 1: Tạo Context mới bằng cách sao chép context hiện tại và gắn
        // thêm 3 giá trị tracing. Nếu header không tồn tại, hệ thống tự sinh
        // UUID để đảm bảo luôn có giá trị hợp lệ cho log/audit.
        Context ctx = Context.current()
                .withValue(CORRELATION_ID, firstOrRandom(headers, "x-correlation-id"))
                .withValue(CAUSATION_ID,   firstOrRandom(headers, "x-causation-id"))
                .withValue(OPERATION_ID,   firstOrRandom(headers, "x-operation-id"));

        // Bước 2: Chuyển tiếp cuộc gọi xuống handler kế tiếp, đồng thời khiến
        // mọi lệnh gọi blocking/async đều nhìn thấy Context mới này.
        return Contexts.interceptCall(ctx, call, headers, next);
    }

    /**
     * Lấy giá trị header đầu tiên có tên {@code name} từ metadata; nếu không có
     * thì sinh ngẫu nhiên một UUID để đảm bảo luôn có giá trị hợp lệ.
     *
     * @param headers metadata của cuộc gọi gRPC
     * @param name    tên header cần đọc (ví dụ {@code x-correlation-id})
     * @return giá trị header đọc được hoặc UUID ngẫu nhiên nếu header vắng mặt
     */
    private static String firstOrRandom(Metadata headers, String name) {
        // Bước 1: Tạo khóa với marshaller ASCII phù hợp với định dạng header
        // mà client kỳ vọng truyền đi.
        Metadata.Key<String> key = Metadata.Key.of(name, Metadata.ASCII_STRING_MARSHALLER);

        // Bước 2: Đọc giá trị từ metadata.
        String v = headers.get(key);

        // Bước 3: Trả về giá trị nếu có, ngược lại tự sinh UUID mới. Việc tự
        // sinh giúp pipeline logging/audit luôn có dữ liệu để phân tích.
        return v != null ? v : UUID.randomUUID().toString();
    }
}
