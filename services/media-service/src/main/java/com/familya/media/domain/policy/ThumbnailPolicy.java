package com.familya.media.domain.policy;

/**
 * Chính sách tạo ảnh thu nhỏ (thumbnail) cho tài sản media.
 *
 * <p>Lớp này đóng vai trò là nguồn chân lý duy nhất (single source of truth) cho
 * các tham số tạo thumbnail trong toàn bộ dịch vụ media. Bằng cách tập trung
 * các hằng số tại đây, các module khác (xử lý ảnh, hiển thị, caching) có thể
 * đồng bộ cùng một cấu hình, tránh tình trạng kích thước hoặc định dạng
 * thumbnail bị phân tán và khó bảo trì.
 *
 * <p>Lớp là {@code final} với constructor riêng, không cho phép kế thừa hay
 * khởi tạo - chỉ được sử dụng như một bộ chứa hằng số tĩnh.
 */
public final class ThumbnailPolicy {
    /**
     * Chiều rộng cố định của thumbnail, tính theo pixel.
     */
    public static final int WIDTH = 480;

    /**
     * Chiều cao cố định của thumbnail, tính theo pixel.
     */
    public static final int HEIGHT = 480;

    /**
     * Định dạng MIME đầu ra của thumbnail (WebP cho kích thước nhỏ và chất lượng tốt).
     */
    public static final String MIME = "image/webp";

    // Ngăn chặn việc khởi tạo lớp tiện ích - chỉ cung cấp các hằng số tĩnh.
    private ThumbnailPolicy() { }
}
