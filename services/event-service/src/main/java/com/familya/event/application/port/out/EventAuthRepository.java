package com.familya.event.application.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Cổng ra (output port) dùng để truy vấn và cập nhật <b>projection phân
 * quyền cục bộ</b> ({@code authorization_projection}).
 *
 * <p>Projection này được tái dựng từ event stream {@code tree.memberships.v1}
 * của <b>treeaccess-service</b> bởi
 * {@link com.familya.event.adapter.in.kafka.ProjectionConsumer}. Mọi use
 * case cần kiểm tra quyền (tạo, cập nhật, tombstone sự kiện) sẽ tham
 * chiếu qua cổng này.
 *
 * <h2>Lý do dùng projection thay vì khóa ngoại</h2>
 * <p>event-service không nắm giữ bảng user trực tiếp — việc chỉ đọc
 * projection cục bộ đảm bảo:
 * <ul>
 *   <li>Tách biệt nghiệp vụ (bounded context) rạch ròi.</li>
 *   <li>Có thể hoạt động khi projection tạm thời bị stale (sẽ tự cập nhật).</li>
 *   <li>Tăng tốc độ đọc (đã được materialize).</li>
 * </ul>
 *
 * @author gia-pha platform
 */
public interface EventAuthRepository {

    /**
     * Tra cứu bản ghi phân quyền cho cặp {@code (treeId, userId)}.
     *
     * @param treeId định danh cây.
     * @param userId định danh người dùng.
     * @return {@link Optional} chứa {@link EventAuthRow} nếu tồn tại,
     *         ngược lại trả về {@link Optional#empty()}.
     */
    Optional<EventAuthRow> findAuth(UUID treeId, UUID userId);

    /**
     * Thêm mới hoặc cập nhật một bản ghi phân quyền.
     *
     * <p>Cần đảm bảo <i>idempotent</i> theo {@code (treeId, userId)}.
     *
     * @param row bản ghi cần ghi.
     */
    void upsertAuth(EventAuthRow row);

    /**
     * Bản ghi vật lý của projection phân quyền.
     *
     * @param treeId          cây gia phả.
     * @param userId          người dùng.
     * @param role            vai trò (EDITOR, ADMIN, VIEWER...) hoặc {@code null} nếu đã thu hồi.
     * @param revision        revision ngữ nghĩa của bản ghi.
     * @param epoch           epoch phục vụ cho đồng bộ chéo dịch vụ.
     * @param grantedAt       thời điểm cấp quyền ban đầu.
     * @param revoked         {@code true} nếu quyền đã bị thu hồi.
     * @param sourceEventId   event ID nguồn đã tạo/cập nhật bản ghi này (cho audit).
     * @param lastUpdatedAt   thời điểm cập nhật gần nhất.
     */
    record EventAuthRow(UUID treeId, UUID userId, String role,
                         long revision, long epoch,
                         java.time.Instant grantedAt, boolean revoked,
                         String sourceEventId, java.time.Instant lastUpdatedAt) {

        /**
         * Cho biết quyền có hiệu lực hay không.
         *
         * @return {@code true} nếu vai trò đã bị thu hồi hoặc chưa được cấp.
         */
        public boolean isRevoked() { return revoked || role == null; }

        /**
         * Cho biết người dùng có quyền chỉnh sửa hay không.
         *
         * @return {@code true} nếu vai trò là {@code ADMIN} hoặc {@code EDITOR}.
         */
        public boolean canEdit() { return role != null && (role.equals("ADMIN") || role.equals("EDITOR")); }
    }
}
