package com.familya.media.domain.exception;

/**
 * Ngoại lệ được ném ra khi một capability (khả năng/quyền hạn) đã bị vô hiệu hóa.
 *
 * <p>Được sử dụng khi hệ thống phát hiện một capability - ví dụ: token truy cập
 * tạm thời, chữ ký số, hoặc quyền upload - đã hết hạn, bị thu hồi hoặc không
 * còn hợp lệ vì lý do bảo mật. Lớp này giúp tầng xử lý phân biệt được lỗi này
 * với các lỗi runtime khác để đưa ra phản hồi phù hợp (ví dụ: yêu cầu cấp lại
 * quyền thay vì thử lại thao tác).
 */
public class CapabilityInvalidatedException extends RuntimeException {
    /**
     * Khởi tạo ngoại lệ với thông điệp mô tả.
     *
     * @param message thông điệp giải thích lý do capability bị vô hiệu hóa
     */
    public CapabilityInvalidatedException(String message) { super(message); }
}
