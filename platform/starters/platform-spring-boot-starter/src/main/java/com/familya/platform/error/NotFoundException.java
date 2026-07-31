package com.familya.platform.error;

import org.springframework.http.HttpStatus;

/**
 * Ngoại lệ được ném ra khi không tìm thấy tài nguyên được yêu cầu.
 *
 * <p>Được {@link GlobalErrorHandler} ánh xạ sang phản hồi HTTP {@code 404 NOT_FOUND}
 * với mã lỗi ổn định {@code "not.found"}. Lớp này được sử dụng cho mọi trường
 * hợp tra cứu thất bại trong các dịch vụ: không tìm thấy thành viên, không
 * tìm thấy cây gia phả, không tìm thấy sự kiện, v.v.</p>
 *
 * @author Family Tree Platform Team
 */
public class NotFoundException extends DomainException {

    /**
     * Khởi tạo ngoại lệ với thông điệp mô tả cụ thể.
     *
     * @param message thông điệp mô tả tài nguyên không tìm thấy
     */
    public NotFoundException(String message) {
        // Gọi constructor lớp cha với HTTP 404 và mã lỗi "not.found".
        super(HttpStatus.NOT_FOUND, "not.found", message);
    }
}
