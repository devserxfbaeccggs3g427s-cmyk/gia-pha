package com.familya.treeaccess.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Authorization projection row. The local copy in Tree Access Service
 * is authoritative; other services consume events into their own local
 * copies. Carries the revision/epoch so consumers can compare against
 * the tree's current revision and detect staleness.
 */
/**
 * Hàng projection phân quyền được lưu trữ cục bộ trong tree-access-service.
 * Đây là projection chính (authoritative); các service khác sẽ tiêu thụ event
 * để tái tạo projection cục bộ của họ. Mang theo revision/epoch để consumer
 * so sánh với revision hiện tại của cây và phát hiện staleness.
 *
 * @param treeId          mã cây
 * @param userId          UUID người dùng
 * @param role            vai trò phân quyền hoặc {@code null} nếu đã thu hồi
 * @param revision        revision tại thời điểm cập nhật
 * @param epoch           epoch tại thời điểm cập nhật
 * @param grantedAt       thời điểm cấp quyền
 * @param revoked         cờ thu hồi
 * @param sourceEventId   mã sự kiện nguồn dùng để truy vết
 * @param lastUpdatedAt   thời điểm cập nhật gần nhất
 */
public record AuthorizationProjection(
        UUID treeId,
        UUID userId,
        TreeMembership.Role role,
        long revision,
        long epoch,
        Instant grantedAt,
        boolean revoked,
        String sourceEventId,
        Instant lastUpdatedAt
) {
    /**
     * @return {@code true} nếu vai trò có quyền edit (và chưa bị thu hồi)
     */
    public boolean canEdit() { return role != null && role.canEdit(); }
    /**
     * @return {@code true} nếu vai trò có quyền xem (và chưa bị thu hồi)
     */
    public boolean canView() { return role != null && role.canView(); }
}