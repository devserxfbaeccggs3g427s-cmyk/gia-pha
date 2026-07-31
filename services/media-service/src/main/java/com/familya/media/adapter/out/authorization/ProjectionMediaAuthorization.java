package com.familya.media.adapter.out.authorization;

import com.familya.media.application.port.out.MediaAuthRepository;
import com.familya.media.application.port.out.MediaAuthorization;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Adapter đầu ra (outbound) — triển khai {@link MediaAuthorization} dựa trên
 * projection nội bộ ({@code authorization_projection}).
 * <p>
 * Các trạng thái quyết định:
 * <ul>
 *   <li>{@link MediaAuthorization.State#ABSENT} — không có row projection.</li>
 *   <li>{@link MediaAuthorization.State#DENY} — row tồn tại nhưng {@code revoked} hoặc role không cho edit.</li>
 *   <li>{@link MediaAuthorization.State#STALE} — projection revision cũ hơn revision kỳ vọng.</li>
 *   <li>{@link MediaAuthorization.State#ALLOW} — đủ điều kiện.</li>
 * </ul>
 */
@Component
public class ProjectionMediaAuthorization implements MediaAuthorization {

    private final MediaAuthRepository repo;

    /**
     * Khởi tạo adapter.
     *
     * @param repo repository đọc projection {@code authorization_projection}.
     */
    public ProjectionMediaAuthorization(MediaAuthRepository repo) {
        this.repo = repo;
    }

    /**
     * Đánh giá quyền truy cập dựa trên projection.
     * <p>
     * Thứ tự kiểm tra (fail-closed):
     * <ol>
     *   <li>Không có row → {@link State#ABSENT}.</li>
     *   <li>{@code revoked=true} → {@link State#DENY}.</li>
     *   <li>Role không đủ quyền edit → {@link State#DENY}.</li>
     *   <li>Projection revision &lt; expectedRevision → {@link State#STALE}.</li>
     *   <li>Còn lại → {@link State#ALLOW}.</li>
     * </ol>
     *
     * @param treeId           UUID cây.
     * @param userId           UUID người dùng.
     * @param expectedRevision revision tối thiểu của projection để tránh stale.
     * @return {@link Decision} mô tả kết quả.
     */
    @Override
    public Decision authorize(UUID treeId, UUID userId, long expectedRevision) {
        Optional<MediaAuthRepository.MediaAuthRow> row = repo.findAuth(treeId, userId);
        // Fail-closed: nếu không có row thì ABSENT — caller tự quyết định deny.
        if (row.isEmpty()) {
            return new Decision(State.ABSENT, "no projection row");
        }
        var r = row.get();
        // Đã revoke → chặn ngay, kể cả khi còn role.
        if (r.isRevoked()) {
            return new Decision(State.DENY, "revoked");
        }
        // Role không đủ quyền chỉnh sửa → DENY với lý do cụ thể.
        if (!r.canEdit()) {
            return new Decision(State.DENY, "role=" + r.role());
        }
        // Stale: projection chưa theo kịp revision upstream → yêu cầu client refresh.
        if (r.revision() < expectedRevision) {
            return new Decision(State.STALE, "projection revision " + r.revision() + " < expected " + expectedRevision);
        }
        return new Decision(State.ALLOW, "ok");
    }
}
