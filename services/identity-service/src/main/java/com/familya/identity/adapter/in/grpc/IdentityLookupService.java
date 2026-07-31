package com.familya.identity.adapter.in.grpc;

import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.UUID;

/**
 * Adapter gRPC đầu vào cung cấp thao tác tra cứu danh tính tối giản.
 *
 * <p>Service này được các microservice khác (ví dụ: {@code tree-service},
 * {@code member-service}) gọi để xác minh sự tồn tại của {@code userId}
 * trong cơ sở dữ liệu identity mà không cần truy cập trực tiếp vào
 * bảng {@code users}. Đây là một hợp đồng (contract) gRPC được sinh
 * tự động từ file {@code contracts/grpc/identity/IdentityLookup.proto}.
 *
 * <p>Trong môi trường CI, lớp {@link IdentityLookupGrpc.IdentityLookupImplBase}
 * được tạo ra bởi {@code protobuf-maven-plugin}; trong môi trường phát
 * triển cục bộ, service sử dụng lớp stand-in đã được định nghĩa sẵn.
 * Chữ ký phương thức công khai được giữ nguyên đồng nhất giữa hai phiên bản.
 *
 * @author Familya Platform Team
 * @since 1.0.0
 */
@GrpcService
public class IdentityLookupService extends IdentityLookupGrpc.IdentityLookupImplBase {

    /**
     * Xử lý yêu cầu tra cứu người dùng theo {@code userId}.
     *
     * <p>Quy trình xử lý diễn ra theo các bước:
     * <ol>
     *     <li>Tạo {@link IdentityLookupResponse.Builder} để xây dựng
     *         phản hồi trả về cho client.</li>
     *     <li>Thử chuyển chuỗi {@code request.getUserId()} sang {@link UUID}.
     *         Nếu thành công, đánh dấu {@code found = true} và echo lại
     *         {@code userId} đã chuẩn hóa. Đây là cách xác nhận tối thiểu
     *         rằng {@code userId} có định dạng hợp lệ – trong tương lai
     *         có thể mở rộng tra cứu thực tế vào cơ sở dữ liệu.</li>
     *     <li>Nếu chuỗi không phải UUID hợp lệ, ngoại lệ
     *         {@link IllegalArgumentException} được bắt lại và phản hồi
     *         được đánh dấu {@code found = false}. Chúng tôi không phát
     *         sinh lỗi gRPC để giữ hợp đồng idempotent và thân thiện
     *         với client.</li>
     *     <li>Gửi phản hồi thông qua {@code responseObserver.onNext(...)}
     *         rồi đóng stream bằng {@code responseObserver.onCompleted()}.</li>
     * </ol>
     *
     * @param request          yêu cầu gRPC chứa {@code userId} cần tra cứu.
     * @param responseObserver observer phía server dùng để đẩy phản hồi về client.
     */
    @Override
    public void lookup(IdentityLookupRequest request, StreamObserver<IdentityLookupResponse> responseObserver) {
        // Bước 1: khởi tạo builder cho phản hồi để có thể thiết lập từng trường.
        IdentityLookupResponse.Builder b = IdentityLookupResponse.newBuilder();
        try {
            // Bước 2: chuyển đổi userId sang UUID để xác nhận định dạng hợp lệ.
            UUID id = UUID.fromString(request.getUserId());
            // Nếu hợp lệ, đánh dấu tìm thấy và echo lại userId đã chuẩn hóa.
            b.setUserId(id.toString()).setFound(true);
        } catch (IllegalArgumentException e) {
            // Bước 3: chuỗi không phải UUID hợp lệ -> phản hồi found = false
            // mà không phát sinh lỗi gRPC, giữ hợp đồng thân thiện với client.
            b.setFound(false);
        }
        // Bước 4: gửi phản hồi duy nhất về client rồi đóng stream.
        responseObserver.onNext(b.build());
        responseObserver.onCompleted();
    }
}
