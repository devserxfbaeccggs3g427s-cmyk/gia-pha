package com.familya.sharing.application.port.in;

import java.util.UUID;

/**
 * Lệnh đầu vào yêu cầu thu hồi (revoke) một liên kết chia sẻ cụ thể.
 * <p>
 * Lệnh này được REST controller đóng gói từ yêu cầu HTTP {@code DELETE}
 * và chuyển cho {@code RevokeShareLinkUseCase} xử lý.
 *
 * @param shareId              định danh liên kết cần thu hồi.
 * @param actingUser           định danh người dùng thực hiện hành động
 *                             (dùng cho phân quyền).
 * @param expectedVersion      phiên bản kỳ vọng của liên kết (phục vụ kiểm tra
 *                             optimistic concurrency &mdash; {@code 0} nghĩa là
 *                             không yêu cầu kiểm tra).
 * @param expectedTreeRevision phiên bản kỳ vọng của cây gia phả tương ứng
 *                             (dùng cho kiểm tra tránh ghi đè).
 * @param reason               lý do thu hồi (tùy chọn, có thể {@code null}).
 */
public record RevokeShareLinkCommand(
        UUID shareId,
        UUID actingUser,
        long expectedVersion,
        long expectedTreeRevision,
        String reason) {
}