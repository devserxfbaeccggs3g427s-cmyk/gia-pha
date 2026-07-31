package com.familya.sharing.application.port.in;

import java.time.Instant;
import java.util.UUID;

/**
 * Lệnh (command) đầu vào yêu cầu tạo mới một liên kết chia sẻ (share link).
 * <p>
 * Đây là một {@code record} bất biến đóng vai trò DTO cho tầng ứng dụng
 * (application layer), được các REST controller ở biên ngoài chuyển đổi từ
 * yêu cầu HTTP rồi truyền vào {@code CreateShareLinkUseCase}.
 * <p>
 * Các trường được validate thông qua Bean Validation (JSR-380) ở tầng adapter.
 *
 * @param treeId               định danh cây gia phả mà liên kết được tạo ra.
 * @param actingUser           định danh người dùng thực hiện hành động (dùng cho phân quyền).
 * @param expectedTreeRevision phiên bản kỳ vọng của cây tại thời điểm tạo
 *                             (dùng cho kiểm tra tránh ghi đè &mdash; nếu {@code 0}
 *                             nghĩa là không yêu cầu kiểm tra).
 * @param scope                phạm vi chia sẻ ở dạng chuỗi (sẽ được chuyển sang
 *                             {@link com.familya.sharing.domain.model.ShareLink.Scope}).
 * @param targetId             định danh mục tiêu trong phạm vi (có thể {@code null}).
 * @param role                 vai trò ở dạng chuỗi (mặc định {@code VIEWER} nếu {@code null}).
 * @param expiresAt            thời điểm hết hạn của liên kết (có thể {@code null}).
 */
public record CreateShareLinkCommand(
        UUID treeId,
        UUID actingUser,
        long expectedTreeRevision,
        String scope,
        UUID targetId,
        String role,
        Instant expiresAt) {
}