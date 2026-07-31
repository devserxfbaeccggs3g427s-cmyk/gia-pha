/**
 * Lớp giả lập (stand-in) cho {@code AuditOpsLookupImplBase} được sinh ra
 * bởi trình biên dịch protobuf. Lớp này phản chiếu interface được tạo ra
 * từ file {@code contracts/grpc/auditops/audit_ops.proto}.
 *
 * <p>Trong pipeline CI, file này được thay thế bằng mã được sinh tự động;
 * chữ ký public của các phương thức phải được giữ nguyên để không phá vỡ
 * lớp triển khai {@link AuditOpsLookupService}.</p>
 */
package com.familya.auditops.adapter.in.grpc;

import io.grpc.stub.StreamObserver;

/**
 * Chứa lớp abstract {@code AuditOpsLookupImplBase} đóng vai trò base cho
 * mọi triển khai RPC của dịch vụ. Lớp ngoài không thể khởi tạo.
 */
public final class AuditOpsProtoGrpc {
    private AuditOpsProtoGrpc() { }

    /**
     * Base class trừu tượng cho mọi triển khai dịch vụ gRPC
     * {@code AuditOpsLookup}. Triển khai mặc định sẽ ném
     * {@link UnsupportedOperationException} để buộc lớp con override.
     *
     * <p>Triển khai thực tế được cung cấp bởi
     * {@link AuditOpsLookupService}.</p>
     */
    public static abstract class AuditOpsLookupImplBase implements io.grpc.BindableService {

        /**
         * RPC lấy trạng thái của một operation. Mặc định ném exception.
         *
         * @param request          yêu cầu từ client
         * @param responseObserver stream observer để gửi phản hồi
         * @throws UnsupportedOperationException nếu chưa được override
         */
        public void getOperation(AuditOpsProto.GetOperationRequest request,
                                 StreamObserver<AuditOpsProto.GetOperationResponse> responseObserver) {
            throw new UnsupportedOperationException("getOperation() must be overridden");
        }

        /**
         * RPC liệt kê các step Saga của một operation. Mặc định ném exception.
         *
         * @param request          yêu cầu từ client
         * @param responseObserver stream observer để gửi phản hồi
         * @throws UnsupportedOperationException nếu chưa được override
         */
        public void listSteps(AuditOpsProto.ListStepsRequest request,
                              StreamObserver<AuditOpsProto.ListStepsResponse> responseObserver) {
            throw new UnsupportedOperationException("listSteps() must be overridden");
        }

        /**
         * Đăng ký service với gRPC server. Trả về {@code ServerServiceDefinition}
         * cho {@code familya.auditops.v1.AuditOpsLookup} (cấu hình tối thiểu
         * vì việc đăng ký thực sự do lớp con {@code bindService()} sinh ra quản lý).
         *
         * @return {@code ServerServiceDefinition} rỗng
         */
        @Override
        public io.grpc.ServerServiceDefinition bindService() {
            return io.grpc.ServerServiceDefinition.builder("familya.auditops.v1.AuditOpsLookup").build();
        }
    }
}