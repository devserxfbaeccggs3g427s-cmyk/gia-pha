package com.familya.sharing.domain.exception;

/**
 * Ngoại lệ được ném ra khi không thể tìm thấy hoặc truy cập một liên kết chia sẻ
 * (share link) trong bối cảnh nghiệp vụ của sharing-service.
 * <p>
 * Ngoại lệ này được sử dụng cho nhiều trường hợp khác nhau trong miền nghiệp vụ:
 * <ul>
 *     <li>Token truy cập không tồn tại trong hệ thống.</li>
 *     <li>Liên kết đã bị thu hồi trước đó.</li>
 *     <li>Liên kết đã hết hạn.</li>
 *     <li>Dữ liệu công khai (projection) cho phạm vi/mục tiêu được yêu cầu không khả dụng.</li>
 * </ul>
 * Thông điệp lỗi nên là một mã lỗi ngắn gọn (ví dụ: {@code "share.unknown"},
 * {@code "share.revoked"}, {@code "share.expired"}, {@code "media.unavailable"}...)
 * để lớp trình bày (REST controller, mapping ngoại lệ) có thể dịch sang phản hồi
 * HTTP phù hợp.
 * <p>
 * Đây là {@link RuntimeException} &mdash; không bắt buộc khai báo trong chữ ký
 * phương thức, giữ cho mã nghiệp vụ gọn gàng.
 */
public class ShareNotFoundException extends RuntimeException {
    /**
     * Khởi tạo một ngoại lệ mới với thông điệp chi tiết.
     *
     * @param message thông điệp/mã lỗi mô tả nguyên nhân không tìm thấy liên kết chia sẻ.
     */
    public ShareNotFoundException(String message) { super(message); }
}