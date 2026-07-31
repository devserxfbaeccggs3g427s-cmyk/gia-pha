package com.familya.platform.error;

import org.springframework.http.HttpStatus;

/**
 * Ngoại lệ được ném ra khi phát hiện xung đột về phiên bản (version) giữa
 * thao tác của client và trạng thái hiện tại của aggregate.
 *
 * <p>Đây là cơ chế kiểm soát tương tranh lạc quan (optimistic concurrency control)
 * — client gửi kèm phiên bản kỳ vọng, nếu phiên bản thực tế trong database đã
 * thay đổi thì thao tác bị từ chối. Được {@link GlobalErrorHandler} ánh xạ sang
 * phản hồi HTTP {@code 409 CONFLICT} với mã lỗi ổn định {@code "version.conflict"}.</p>
 *
 * <p>Client nên xử lý lỗi này bằng cách tải lại trạng thái mới nhất, hợp nhất
 * thay đổi và thử lại thao tác.</p>
 *
 * @author Family Tree Platform Team
 */
public class OptimisticConcurrencyException extends DomainException {

    /**
     * Khởi tạo ngoại lệ với thông điệp mô tả cụ thể.
     *
     * @param message thông điệp mô tả xung đột phiên bản (ví dụ: ID phiên bản cũ)
     */
    public OptimisticConcurrencyException(String message) {
        // Gọi constructor lớp cha với HTTP 409 và mã lỗi "version.conflict".
        super(HttpStatus.CONFLICT, "version.conflict", message);
    }
}
