package com.familya.sharing.application.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Cổng (port) truy cập bảng {@code authorization_projection} &mdash; bảng lưu
 * trữ thông tin phân quyền theo cây/người dùng được đồng bộ từ các sự kiện
 * tư cách thành viên (membership events).
 * <p>
 * Đây là cơ sở dữ liệu mà {@code ProjectionShareAuthorization} dựa vào để
 * đưa ra quyết định cấp quyền khi tạo/thu hồi liên kết chia sẻ.
 */
public interface ShareAuthRepository {

    /**
     * Tra cứu một bản ghi phân quyền dựa trên cây và người dùng.
     *
     * @param treeId định danh cây gia phả.
     * @param userId định danh người dùng.
     * @return {@link Optional} chứa {@link AuthRow} nếu tồn tại, {@link Optional#empty()} nếu không.
     */
    Optional<AuthRow> find(UUID treeId, UUID userId);

    /**
     * Thực hiện thêm mới hoặc cập nhật một bản ghi phân quyền (upsert).
     * <p>
     * Phương thức này phải được gọi trong một transaction hiện có ở tầng
     * adapter (xem {@code Propagation.MANDATORY}).
     *
     * @param treeId         định danh cây gia phả.
     * @param userId         định danh người dùng.
     * @param role           vai trò (có thể {@code null} khi thu hồi).
     * @param revision       phiên bản revision của cây tại thời điểm ghi.
     * @param epoch          epoch tương ứng.
     * @param grantedAt      thời điểm cấp quyền.
     * @param revoked        {@code true} nếu quyền đã bị thu hồi.
     * @param sourceEventId  định danh sự kiện nguồn phát sinh thay đổi.
     */
    void upsert(UUID treeId, UUID userId, String role, long revision, long epoch,
                java.time.Instant grantedAt, boolean revoked, String sourceEventId);

    /**
     * Bản ghi phân quyền của một người dùng trên một cây gia phả.
     * <p>
     * Là một {@code record} bất biến. Cung cấp thêm các phương thức tiện ích
     * {@link #isRevoked()} và {@link #canShare()} để phục vụ việc ra quyết
     * định phân quyền ở tầng cao hơn.
     *
     * @param treeId        định danh cây gia phả.
     * @param userId        định danh người dùng.
     * @param role          vai trò hiện tại (có thể {@code null} nếu đã thu hồi).
     * @param revision      phiên bản revision của cây.
     * @param epoch         epoch tương ứng.
     * @param revoked       {@code true} nếu bản ghi đã đánh dấu thu hồi.
     * @param lastUpdatedAt thời điểm cập nhật lần cuối.
     */
    record AuthRow(UUID treeId, UUID userId, String role, long revision, long epoch,
                   boolean revoked, java.time.Instant lastUpdatedAt) {
        /**
         * @return {@code true} nếu bản ghi đã bị thu hồi hoặc vai trò đã bị xóa.
         */
        public boolean isRevoked() { return revoked || role == null; }

        /**
         * @return {@code true} nếu người dùng có quyền tạo liên kết chia sẻ
         *         (chỉ {@code ADMIN} hoặc {@code EDITOR} mới có quyền này).
         */
        public boolean canShare() { return role != null && (role.equals("ADMIN") || role.equals("EDITOR")); }
    }
}