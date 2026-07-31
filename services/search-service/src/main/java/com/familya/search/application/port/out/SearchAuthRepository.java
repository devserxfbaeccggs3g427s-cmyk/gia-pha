package com.familya.search.application.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Cổng (port) chỉ-đọc tới projection ủy quyền, được các use case search tham
 * chiếu.
 *
 * <p>Service search không bao giờ tự ra quyết định phân quyền mang tính
 * "nguồn chân lý" (source-of-truth). Nó chỉ kiểm tra bản ghi projection ủy
 * quyền có tồn tại và chưa bị thu hồi hay không, đồng thời đọc phiên bản
 * quan sát được để gắn kèm trong kết quả tìm kiếm.</p>
 */
public interface SearchAuthRepository {

    /**
     * Tìm bản ghi projection ủy quyền cho cặp {@code (treeId, userId)}.
     *
     * @param treeId định danh cây gia phả.
     * @param userId định danh người dùng.
     * @return {@code Optional} chứa {@link AuthRow} nếu có, ngược lại rỗng.
     */
    Optional<AuthRow> find(UUID treeId, UUID userId);

    /**
     * Một hàng projection ủy quyền.
     *
     * @param treeId         định danh cây.
     * @param userId         định danh người dùng.
     * @param role           vai trò (OWNER, EDITOR, VIEWER...) hoặc {@code null}
     *                       nếu đã bị thu hồi.
     * @param revision       phiên bản cây mà bản ghi này phản ánh.
     * @param revoked        cờ thu hồi.
     * @param lastUpdatedAt  thời điểm cập nhật cuối.
     */
    record AuthRow(UUID treeId, UUID userId, String role, long revision,
                   boolean revoked, java.time.Instant lastUpdatedAt) {
        /**
         * @return {@code true} nếu bản ghi đã bị thu hồi hoặc không còn vai trò.
         */
        public boolean isRevoked() { return revoked || role == null; }
    }
}
