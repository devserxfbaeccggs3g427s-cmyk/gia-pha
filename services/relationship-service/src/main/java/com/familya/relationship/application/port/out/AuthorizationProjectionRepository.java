package com.familya.relationship.application.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Cổng truy vấn projection phân quyền cục bộ.
 * <p>
 * Projection này được xây dựng từ Kafka stream {@code tree.memberships.v1} của
 * Tree service (xem {@code ProjectionConsumer.onMembership}). Mỗi dòng trong
 * bảng {@code authorization_projection} đại diện cho một cặp {@code (treeId,
 * userId)} cùng vai trò (ADMIN/EDITOR/VIEWER) và trạng thái (cấp/thu hồi).
 * </p>
 *
 * <p>
 * Đây là projection "cuối cùng đã biết" tại thời điểm xử lý; client có thể
 * truyền kèm {@code expectedRevision} để phát hiện stale projection.
 * </p>
 */
public interface AuthorizationProjectionRepository {

    /**
     * Tra cứu một dòng phân quyền cho cặp {@code (treeId, userId)}.
     *
     * @param treeId định danh cây gia phả
     * @param userId định danh người dùng
     * @return {@code Optional} chứa dòng phân quyền hoặc rỗng nếu chưa có
     */
    Optional<RelationshipAuthRow> findAuth(UUID treeId, UUID userId);

    /**
     * Cập nhật (upsert) một dòng phân quyền.
     *
     * @param row dòng phân quyền cần lưu
     */
    void upsertAuth(RelationshipAuthRow row);

    /**
     * Bản ghi (record) đại diện cho một dòng trong bảng phân quyền.
     *
     * @param treeId        định danh cây gia phả
     * @param userId        định danh người dùng
     * @param role          vai trò (ADMIN, EDITOR, VIEWER hoặc null khi thu hồi)
     * @param revision      phiên bản projection
     * @param epoch         epoch tương ứng
     * @param grantedAt     thời điểm được cấp quyền
     * @param revoked       cờ thu hồi
     * @param sourceEventId định danh sự kiện nguồn (cho idempotency)
     * @param lastUpdatedAt thời điểm cập nhật cuối cùng
     */
    record RelationshipAuthRow(UUID treeId, UUID userId, String role,
                                long revision, long epoch,
                                java.time.Instant grantedAt, boolean revoked,
                                String sourceEventId, java.time.Instant lastUpdatedAt) {

        /**
         * @return {@code true} nếu dòng này đã bị thu hồi hoặc vai trò rỗng.
         */
        public boolean isRevoked() { return revoked || role == null; }

        /**
         * Kiểm tra người dùng có quyền chỉnh sửa (edit) hay không.
         * <p>
         * Theo mặc định, chỉ ADMIN và EDITOR được phép chỉnh sửa; VIEWER chỉ
         * được đọc. Vai trò khác hoặc rỗng được coi là không có quyền.
         * </p>
         *
         * @return {@code true} nếu người dùng có quyền chỉnh sửa
         */
        public boolean canEdit() { return role != null && (role.equals("ADMIN") || role.equals("EDITOR")); }
    }
}