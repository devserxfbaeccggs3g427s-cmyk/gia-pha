package com.familya.search.application.port.out;

import java.util.UUID;

/**
 * Cổng (port) kiểm tra quyền truy cập tìm kiếm.
 *
 * <p>Service search không tự ra quyết định phân quyền - nó chỉ tham chiếu
 * projection ủy quyền đã được dịch vụ khác cập nhật, kết hợp với phiên bản
 * cây mà client kỳ vọng. Trả về {@link Decision} với một trong các trạng
 * thái {@link State}.</p>
 */
public interface SearchAuthorization {

    /**
     * Kiểm tra quyền truy cập cho {@code userId} trên {@code treeId} với
     * phiên bản cây mà client kỳ vọng.
     *
     * @param treeId           định danh cây gia phả.
     * @param userId           định danh người dùng.
     * @param expectedRevision phiên bản cây mà client cho là mới nhất.
     * @return quyết định {@link Decision} mô tả trạng thái và lý do.
     */
    Decision authorize(UUID treeId, UUID userId, long expectedRevision);

    /**
     * Các trạng thái quyết định:
     * <ul>
     *   <li>{@link #ALLOW} - cho phép.</li>
     *   <li>{@link #DENY} - đã bị thu hồi (revoked).</li>
     *   <li>{@link #ABSENT} - không có bản ghi projection.</li>
     *   <li>{@link #STALE} - projection cũ hơn {@code expectedRevision}.</li>
     * </ul>
     */
    enum State { ALLOW, DENY, ABSENT, STALE }

    /**
     * Kết quả kiểm tra quyền.
     *
     * @param state  trạng thái quyết định.
     * @param reason chuỗi giải thích ngắn gọn (dùng cho log và thông báo lỗi).
     */
    record Decision(State state, String reason) {
        /**
         * @return {@code true} nếu state là {@link State#ALLOW}.
         */
        public boolean isAllowed() { return state == State.ALLOW; }
    }
}
