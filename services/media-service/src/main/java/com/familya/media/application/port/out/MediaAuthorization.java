package com.familya.media.application.port.out;

import java.util.UUID;

/**
 * Authorization decision port. The implementation must consult the
 * local authorization projection populated from the membership topic.
 * Absent or revoked rows deny unsafe mutations.
 *
 * <p>Port ra (driven port) của kiến trúc hexagonal: cung cấp quyết định
 * phân quyền cho mọi use case ghi. Triển khai phải dựa vào projection
 * {@code MediaAuthRepository} (đã populate từ topic membership).</p>
 *
 * <p>Hợp đồng:</p>
 * <ul>
 *   <li>Row vắng mặt hoặc bị thu hồi → phủ nhận (deny).</li>
 *   <li>{@code expectedRevision} lệch revision hiện tại → {@link State#STALE}
 *       (coi như deny, ngăn race với thu hồi).</li>
 *   <li>Vai trò hợp lệ (ADMIN/EDITOR) mới được {@link State#ALLOW}.</li>
 * </ul>
 */
public interface MediaAuthorization {

    /**
     * Ra quyết định phân quyền cho một hành động ghi lên tree.
     *
     * @param treeId UUID family-tree mà action nhắm vào.
     * @param userId UUID người dùng yêu cầu.
     * @param expectedRevision revision mà caller quan sát được khi quyết
     *                        định bắt đầu; lệch với revision hiện tại sẽ
     *                        trả về {@link State#STALE}.
     * @return {@link Decision} mang trạng thái và lý do.
     */
    Decision authorize(UUID treeId, UUID userId, long expectedRevision);

    /**
     * Trạng thái quyết định phân quyền.
     *
     * <ul>
     *   <li>{@link #ALLOW} – hành động được phép.</li>
     *   <li>{@link #DENY} – hành động bị từ chối (sai role).</li>
     *   <li>{@link #ABSENT} – không có projection cho (tree, user).</li>
     *   <li>{@link #STALE} – caller nhìn revision cũ; coi như deny an
     *       toàn.</li>
     * </ul>
     */
    enum State { ALLOW, DENY, ABSENT, STALE }

    /**
     * Quyết định phân quyền trả về cho caller.
     *
     * @param state  trạng thái (xem {@link State}).
     * @param reason lý do dạng chuỗi; phục vụ log / thông báo lỗi.
     */
    record Decision(State state, String reason) {

        /**
         * @return {@code true} nếu và chỉ nếu {@link #state()} là
         *         {@link State#ALLOW}.
         */
        public boolean isAllowed() { return state == State.ALLOW; }
    }
}
