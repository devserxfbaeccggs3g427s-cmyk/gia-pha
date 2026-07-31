package com.familya.identity.adapter.in.grpc;

/**
 * Lớp stand-in mô phỏng thông điệp {@code IdentityLookupRequest} được
 * sinh tự động từ file Protobuf.
 *
 * <p>Trong môi trường CI, file
 * {@code contracts/grpc/identity/IdentityLookup.proto} được biên dịch
 * bởi {@code protobuf-maven-plugin} và tạo ra lớp có cùng tên với
 * chữ ký phương thức công khai giống hệt. Lớp stand-in này giúp quá
 * trình phát triển cục bộ có thể biên dịch được mà không cần chạy
 * plugin sinh mã trước.
 *
 * <p>Lớp này <strong>không nên</strong> bị sửa đổi thủ công. Mọi thay
 * đổi hợp đồng phải được thực hiện trong file {@code .proto} tương ứng.
 *
 * @author Familya Platform Team
 * @since 1.0.0
 */
public final class IdentityLookupRequest {
    /**
     * Mã định danh người dùng ở dạng chuỗi. Mặc định là chuỗi rỗng
     * thay vì {@code null} để tránh {@link NullPointerException} khi
     * truy cập và đảm bảo tương thích với bản sinh tự động.
     */
    private String userId = "";

    /**
     * Trả về {@code userId} được gửi trong yêu cầu.
     *
     * @return chuỗi định danh người dùng (có thể rỗng nếu client không cung cấp).
     */
    public String getUserId() { return userId; }

    /**
     * Tạo một {@link Builder} rỗng để xây dựng thông điệp yêu cầu.
     *
     * <p>Builder pattern được sử dụng để bắt chước cấu trúc của mã
     * được sinh tự động từ Protobuf.
     *
     * @return {@link Builder} mới.
     */
    public static Builder newBuilder() { return new Builder(); }

    /**
     * Lớp Builder giúp xây dựng {@link IdentityLookupRequest} theo
     * phong cách fluent API.
     *
     * <p>Phương thức {@link #setUserId(String)} chuẩn hóa giá trị
     * {@code null} thành chuỗi rỗng nhằm duy trì tính bất biến của
     * trường {@code userId} (luôn không null).
     */
    public static final class Builder {
        /** Thể hiện đang được xây dựng. */
        private final IdentityLookupRequest r = new IdentityLookupRequest();

        /**
         * Thiết lập {@code userId}. Nếu giá trị truyền vào là
         * {@code null}, giá trị được thay thế bằng chuỗi rỗng để
         * đảm bảo bất biến.
         *
         * @param v giá trị {@code userId} cần thiết lập.
         * @return {@code Builder} hiện tại để hỗ trợ chain.
         */
        public Builder setUserId(String v) { r.userId = v == null ? "" : v; return this; }

        /**
         * Hoàn tất quá trình xây dựng và trả về {@link IdentityLookupRequest}.
         *
         * @return thể hiện đã cấu hình.
         */
        public IdentityLookupRequest build() { return r; }
    }
}
