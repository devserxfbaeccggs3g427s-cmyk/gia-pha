package com.familya.media.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Port ra (driven port) của kiến trúc hexagonal: kho lưu trữ projection
 * phân quyền media-service.
 *
 * <p>Projection này được populate từ topic {@code membership} và là
 * nguồn dữ liệu mà {@link MediaAuthorization} dựa vào để ra quyết
 * định. Hợp đồng:</p>
 *
 * <ul>
 *   <li>Một {@code treeId} + {@code userId} chỉ có tối đa một row hiện
 *       hành.</li>
 *   <li>Cập nhật tăng đơn điệu {@code revision} / {@code epoch} để caller
 *       phát hiện stale.</li>
 *   <li>{@code revoked = true} hoặc {@code role == null} tương đương
 *       "không có quyền".</li>
 * </ul>
 */
public interface MediaAuthRepository {

    /**
     * Tra cứu quyền hiện hành cho cặp (tree, user).
     *
     * @param treeId UUID family-tree.
     * @param userId UUID người dùng.
     * @return {@code Optional} chứa row; rỗng nếu user chưa từng được
     *         cấp quyền trên tree.
     */
    Optional<MediaAuthRow> findAuth(UUID treeId, UUID userId);

    /**
     * Chèn hoặc cập nhật row phân quyền.
     *
     * @param row bản ghi mới; nếu đã tồn tại sẽ được cập nhật theo khóa
     *            (treeId, userId).
     */
    void upsertAuth(MediaAuthRow row);

    /**
     * Một dòng phân quyền trong projection.
     *
     * @param treeId          UUID family-tree.
     * @param userId          UUID người dùng.
     * @param role            vai trò, vd. {@code ADMIN}, {@code EDITOR},
     *                        {@code VIEWER}; null tương đương revoked.
     * @param revision        revision của row (tăng đơn điệu).
     * @param epoch           epoch cập nhật.
     * @param grantedAt       mốc thời gian cấp quyền lần đầu.
     * @param revoked         cờ thu hồi.
     * @param sourceEventId   id sự kiện nguồn từ membership topic dùng để
     *                        idempotent reprocess.
     * @param lastUpdatedAt   mốc cập nhật lần cuối.
     */
    record MediaAuthRow(UUID treeId, UUID userId, String role,
                        long revision, long epoch,
                        Instant grantedAt, boolean revoked,
                        String sourceEventId, Instant lastUpdatedAt) {

        /**
         * @return {@code true} nếu row đã thu hồi hoặc thiếu role; ngược
         *         lại {@code false}.
         */
        public boolean isRevoked() { return revoked || role == null; }

        /**
         * @return {@code true} nếu user có vai trò {@code ADMIN} hoặc
         *         {@code EDITOR} (được phép ghi).
         */
        public boolean canEdit() { return role != null && (role.equals("ADMIN") || role.equals("EDITOR")); }
    }
}
