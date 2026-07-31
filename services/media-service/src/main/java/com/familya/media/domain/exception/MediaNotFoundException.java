package com.familya.media.domain.exception;

/**
 * Ngoại lệ được ném ra khi không tìm thấy tài sản media theo yêu cầu.
 *
 * <p>Được sử dụng khi người dùng hoặc dịch vụ khác cố gắng truy cập, cập nhật
 * hoặc xóa một {@link com.familya.media.domain.model.MediaAsset} với định danh
 * không tồn tại trong cơ sở dữ liệu, hoặc tài sản đã bị xóa vĩnh viễn
 * (không còn ở trạng thái tombstoned).
 */
public class MediaNotFoundException extends RuntimeException {
    /**
     * Khởi tạo ngoại lệ với thông điệp mô tả.
     *
     * @param message thông điệp giải thích tài sản nào không được tìm thấy
     */
    public MediaNotFoundException(String message) { super(message); }
}
