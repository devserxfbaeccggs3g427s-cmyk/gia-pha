package com.familya.platform.error;

import org.springframework.http.HttpStatus;

/**
 * Ngoại lệ được ném ra khi người dùng đã xác thực nhưng không có đủ quyền
 * để thực hiện hành động được yêu cầu.
 *
 * <p>Được {@link GlobalErrorHandler} ánh xạ sang phản hồi HTTP {@code 403 FORBIDDEN}
 * với mã lỗi ổn định {@code "forbidden"}. Khác với {@code 401 Unauthorized} (chưa xác
 * thực), {@code 403} biểu thị rằng người dùng đã được nhận diện nhưng không có
 * quyền truy cập tài nguyên.</p>
 *
 * <p>Các tình huống sử dụng điển hình:</p>
 * <ul>
 *   <li>Thành viên không phải admin cố gắng truy cập endpoint chỉ dành cho admin.</li>
 *   <li>Người dùng cố gắng thao tác trên cây gia phả mà họ không có quyền truy cập.</li>
 *   <li>Service account thiếu scope/role cần thiết khi gọi API quản trị.</li>
 * </ul>
 *
 * @author Family Tree Platform Team
 */
public class ForbiddenException extends DomainException {

    /**
     * Khởi tạo ngoại lệ với thông điệp mô tả cụ thể.
     *
     * @param message thông điệp mô tả lý do bị từ chối quyền truy cập
     */
    public ForbiddenException(String message) {
        // Gọi constructor của lớp cha với HTTP 403 và mã lỗi ổn định "forbidden".
        super(HttpStatus.FORBIDDEN, "forbidden", message);
    }
}
