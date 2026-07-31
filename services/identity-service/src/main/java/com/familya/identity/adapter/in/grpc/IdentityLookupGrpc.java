package com.familya.identity.adapter.in.grpc;

import io.grpc.stub.StreamObserver;

/**
 * Lớp stand-in đóng vai trò thay thế cho lớp {@code IdentityLookupGrpc}
 * được sinh tự động từ file Protobuf.
 *
 * <p>Trong môi trường CI (build pipeline), file
 * {@code contracts/grpc/identity/IdentityLookup.proto} sẽ được biên dịch
 * bởi plugin {@code protobuf-maven-plugin} và tạo ra lớp
 * {@code IdentityLookupGrpc.IdentityLookupImplBase} cùng tên. Lớp stand-in
 * này tồn tại để:
 * <ul>
 *     <li>Cho phép biên dịch mã nguồn Java cục bộ ngay cả khi mã sinh
 *         tự động chưa được tạo ra.</li>
 *     <li>Giữ nguyên chữ ký phương thức công khai giữa hai phiên bản
 *         (generated và stand-in) để {@link IdentityLookupService} có
 *         thể biên dịch được trong cả hai trường hợp.</li>
 * </ul>
 *
 * <p>Không nên sửa đổi lớp này thủ công; mọi thay đổi về hợp đồng phải
 * được thực hiện trong file {@code .proto} tương ứng.
 *
 * @author Familya Platform Team
 * @since 1.0.0
 */
public final class IdentityLookupGrpc {
    /**
     * Constructor riêng tư để ngăn việc tạo thể hiện của lớp tiện ích này.
     * Lớp chỉ chứa các thành phần tĩnh lồng nhau.
     */
    private IdentityLookupGrpc() { }

    /**
     * Lớp cơ sở trừu tượng mô phỏng {@code IdentityLookupImplBase} được
     * sinh từ file Protobuf.
     *
     * <p>Các lớp kế thừa (chẳng hạn {@link IdentityLookupService}) sẽ
     * ghi đè phương thức {@link #lookup(IdentityLookupRequest, StreamObserver)}
     * để cung cấp logic tra cứu thực tế. Phương thức mặc định ném
     * {@link UnsupportedOperationException} nhằm buộc lập trình viên
     * phải override nó.
     *
     * <p>Phương thức {@link #bindService()} trả về một
     * {@link io.grpc.ServerServiceDefinition} rỗng với tên dịch vụ
     * {@code "familya.identity.v1.IdentityLookup"} – tên này phải khớp
     * với khai báo {@code service} trong file Protobuf.
     */
    public static abstract class IdentityLookupImplBase implements io.grpc.BindableService {
        /**
         * Phương thức tra cứu mặc định. Sẽ ném ngoại lệ nếu lớp con
         * không ghi đè.
         *
         * @param request          yêu cầu gRPC đầu vào.
         * @param responseObserver observer dùng để gửi phản hồi.
         * @throws UnsupportedOperationException luôn luôn, trừ khi được ghi đè.
         */
        public void lookup(IdentityLookupRequest request,
                           StreamObserver<IdentityLookupResponse> responseObserver) {
            throw new UnsupportedOperationException("lookup() must be overridden");
        }

        /**
         * Đăng ký dịch vụ với máy chủ gRPC.
         *
         * <p>Trong bản sinh tự động, phương thức này sẽ gắn tất cả các
         * phương thức RPC đã triển khai vào {@code ServerServiceDefinition}.
         * Trong bản stand-in, chúng tôi chỉ tạo một {@code Builder}
         * sơ khai với tên dịch vụ đúng chuẩn để tránh lỗi khi
         * {@link IdentityLookupService} được khởi tạo bởi
         * {@code grpc-server-starter}.
         *
         * @return {@code ServerServiceDefinition} cho dịch vụ lookup.
         */
        @Override
        public io.grpc.ServerServiceDefinition bindService() {
            return io.grpc.ServerServiceDefinition.builder("familya.identity.v1.IdentityLookup").build();
        }
    }
}
