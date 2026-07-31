package com.familya.sharing.application.port.out;

import com.familya.sharing.domain.model.ShareLink;

import java.util.Optional;
import java.util.UUID;

/**
 * Cổng (port) phân quyền cho sharing-service &mdash; chịu trách nhiệm đưa ra
 * quyết định một người dùng có được phép tạo/thu hồi liên kết chia sẻ trên một
 * cây cụ thể hay không.
 * <p>
 * Cổng này được {@code ProjectionShareAuthorization} cài đặt dựa trên
 * {@link ShareAuthRepository}. Kết quả trả về được biểu diễn bằng
 * {@link Decision} &mdash; một {@code record} chứa {@link State} và lý do.
 */
public interface ShareAuthorization {

    /**
     * Ra quyết định phân quyền cho một hành động chia sẻ của người dùng trên cây.
     *
     * @param treeId           định danh cây gia phả.
     * @param userId           định danh người dùng.
     * @param expectedRevision phiên bản revision kỳ vọng của projection.
     * @return {@link Decision} mang trạng thái và lý do tương ứng.
     */
    Decision authorize(UUID treeId, UUID userId, long expectedRevision);

    /**
     * Trả về vai trò mặc định được cấp cho người dùng khi tạo liên kết chia sẻ.
     *
     * @return vai trò mặc định &mdash; thường là {@link ShareLink.Role#VIEWER}.
     */
    ShareLink.Role defaultRole();

    /**
     * Trạng thái quyết định phân quyền.
     */
    enum State { ALLOW, DENY, ABSENT, STALE }

    /**
     * Quyết định phân quyền &mdash; gồm trạng thái và lý do đi kèm.
     *
     * @param state  trạng thái cho phép/từ chối/không có/stale.
     * @param reason chuỗi lý do ngắn gọn (dùng cho log/giải thích).
     */
    record Decision(State state, String reason) {
        /**
         * @return {@code true} nếu quyết định là cho phép ({@link State#ALLOW}).
         */
        public boolean isAllowed() { return state == State.ALLOW; }
    }
}