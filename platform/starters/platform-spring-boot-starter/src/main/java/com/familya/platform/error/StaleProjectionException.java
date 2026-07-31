package com.familya.platform.error;

import org.springframework.http.HttpStatus;

/**
 * Ngoại lệ được ném ra khi projection (mô hình đọc) không đủ mới để phục vụ
 * một yêu cầu đòi hỏi dữ liệu cập nhật.
 *
 * <p>Được {@link GlobalErrorHandler} ánh xạ sang phản hồi HTTP {@code 409 CONFLICT}
 * với mã lỗi ổn định {@code "projection.stale"}. Lỗi này thường phát sinh khi
 * hệ thống cần dữ liệu fresh (ví dụ: kiểm tra quyền truy cập) nhưng projection
 * chưa kịp được cập nhật từ topic nguồn.</p>
 *
 * <p>Hành vi chuẩn khi gặp ngoại lệ này:</p>
 * <ul>
 *   <li>Với mutation không an toàn: fail closed (từ chối yêu cầu).</li>
 *   <li>Với sensitive read: cho phép gọi RPC khẩn cấp đến dịch vụ nguồn trong
 *       phạm vi {@code emergencyRpcBudget} đã được cấu hình.</li>
 * </ul>
 *
 * @author Family Tree Platform Team
 */
public class StaleProjectionException extends DomainException {

    /**
     * Khởi tạo ngoại lệ với thông điệp mô tả cụ thể.
     *
     * @param message mô tả vì sao projection bị coi là cũ (ví dụ: "độ tuổi vượt ngưỡng")
     */
    public StaleProjectionException(String message) {
        // Gọi constructor lớp cha với HTTP 409 và mã lỗi "projection.stale".
        super(HttpStatus.CONFLICT, "projection.stale", message);
    }
}
