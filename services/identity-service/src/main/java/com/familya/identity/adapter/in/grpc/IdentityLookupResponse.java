package com.familya.identity.adapter.in.grpc;

/**
 * Lớp stand-in mô phỏng thông điệp {@code IdentityLookupResponse} được
 * sinh tự động từ file Protobuf.
 *
 * <p>Trong môi trường CI, file
 * {@code contracts/grpc/identity/IdentityLookup.proto} được biên dịch
 * bởi {@code protobuf-maven-plugin} và tạo ra lớp có cùng tên với
 * chữ ký phương thức công khai giống hệt. Lớp stand-in này giúp quá
 * trình phát triển cục bộ có thể biên dịch mà không cần sinh mã
 * Protobuf trước.
 *
 * <p>Lớp này <strong>không nên</strong> bị sửa đổi thủ công. Mọi thay
 * đổi hợp đồng phải được thực hiện trong file {@code .proto} tương ứng.
 *
 * @author Familya Platform Team
 * @since 1.0.0
 */
public final class IdentityLookupResponse {
    /**
     * Mã định danh người dùng được echo lại trong phản hồi.
     * Mặc định là chuỗi rỗng để đảm bảo tương thích với bản sinh tự động.
     */
    private String userId = "";
    /**
     * Cờ đánh dấu người dùng có tồn tại hay không. Giá trị mặc định
     * là {@code false} – phía client phải kiểm tra trước khi sử dụng.
     */
    private boolean found;

    /**
     * Trả về {@code userId} đã được echo trong phản hồi.
     *
     * @return chuỗi định danh người dùng (chuỗi rỗng nếu không thiết lập).
     */
    public String getUserId() { return userId; }

    /**
     * Trả về cờ cho biết người dùng có tồn tại hay không.
     *
     * <p>Phương thức này có tên {@code getFound} (thay vì {@code isFound})
     * để bắt chước chính xác cách đặt tên của mã sinh tự động từ Protobuf.
     *
     * @return {@code true} nếu tìm thấy người dùng, {@code false} nếu ngược lại.
     */
    public boolean getFound() { return found; }

    /**
     * Tạo {@link Builder} rỗng để xây dựng phản hồi.
     *
     * @return {@link Builder} mới.
     */
    public static Builder newBuilder() { return new Builder(); }

    /**
     * Lớp Builder hỗ trợ xây dựng {@link IdentityLookupResponse} theo
     * phong cách fluent API mô phỏng protobuf-generated code.
     */
    public static final class Builder {
        /** Thể hiện đang được xây dựng. */
        private final IdentityLookupResponse r = new IdentityLookupResponse();

        /**
         * Thiết lập {@code userId} (không null – chuỗi rỗng thay thế
         * cho {@code null}).
         *
         * @param v giá trị {@code userId} cần thiết lập.
         * @return {@code Builder} hiện tại để hỗ trợ chain.
         */
        public Builder setUserId(String v) { r.userId = v == null ? "" : v; return this; }

        /**
         * Thiết lập cờ {@code found}.
         *
         * @param v {@code true} nếu tìm thấy người dùng, {@code false} nếu ngược lại.
         * @return {@code Builder} hiện tại để hỗ trợ chain.
         */
        public Builder setFound(boolean v) { r.found = v; return this; }

        /**
         * Hoàn tất quá trình xây dựng và trả về {@link IdentityLookupResponse}.
         *
         * @return thể hiện đã cấu hình.
         */
        public IdentityLookupResponse build() { return r; }
    }
}
