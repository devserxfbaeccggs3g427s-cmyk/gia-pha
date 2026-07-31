package com.familya.sharing.adapter.out.authorization;

import com.familya.sharing.application.port.out.ShareAuthRepository;
import com.familya.sharing.application.port.out.ShareAuthorization;
import com.familya.sharing.domain.model.ShareLink;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Cài đặt {@link ShareAuthorization} dựa trên projection
 * {@code authorization_projection} (được đồng bộ từ các sự kiện membership).
 * <p>
 * Đây là chiến lược phân quyền "đọc" &mdash; không gọi trực tiếp tới identity
 * service mà dựa vào projection đã được cập nhật từ Kafka. Cách tiếp cận
 * này giúp phân quyền có độ trễ thấp và độc lập với identity-service.
 */
@Component
public class ProjectionShareAuthorization implements ShareAuthorization {

    private final ShareAuthRepository repo;

    /**
     * Khởi tạo adapter.
     *
     * @param repo kho lưu trữ {@link ShareAuthRepository}.
     */
    public ProjectionShareAuthorization(ShareAuthRepository repo) {
        this.repo = repo;
    }

    /**
     * Đưa ra quyết định phân quyền dựa trên projection.
     * <p>
     * Thứ tự kiểm tra:
     * <ol>
     *     <li>Nếu không có bản ghi projection &rarr; {@link State#ABSENT}.</li>
     *     <li>Nếu đã thu hồi &rarr; {@link State#DENY} với lý do "revoked".</li>
     *     <li>Nếu role không đủ quyền chia sẻ (không phải ADMIN/EDITOR) &rarr;
     *         {@link State#DENY} với lý do "role=...".</li>
     *     <li>Nếu revision của projection cũ hơn revision kỳ vọng &rarr;
     *         {@link State#STALE}.</li>
     *     <li>Ngược lại &rarr; {@link State#ALLOW}.</li>
     * </ol>
     *
     * @param treeId           định danh cây gia phả.
     * @param userId           định danh người dùng.
     * @param expectedRevision revision kỳ vọng của projection.
     * @return {@link Decision} tương ứng.
     */
    @Override
    public Decision authorize(UUID treeId, UUID userId, long expectedRevision) {
        // Bước 1: Tra cứu bản ghi phân quyền trong projection.
        var row = repo.find(treeId, userId);
        if (row.isEmpty()) {
            return new Decision(State.ABSENT, "no projection row");
        }
        var r = row.get();

        // Bước 2: Nếu đã bị thu hồi thì từ chối ngay.
        if (r.isRevoked()) {
            return new Decision(State.DENY, "revoked");
        }

        // Bước 3: Nếu role không đủ quyền (chỉ ADMIN/EDITOR mới được chia sẻ).
        if (!r.canShare()) {
            return new Decision(State.DENY, "role=" + r.role());
        }

        // Bước 4: Kiểm tra staleness &mdash; nếu projection cũ hơn kỳ vọng.
        if (r.revision() < expectedRevision) {
            return new Decision(State.STALE, "projection revision " + r.revision() + " < expected " + expectedRevision);
        }

        // Bước 5: Cho phép.
        return new Decision(State.ALLOW, "ok");
    }

    /**
     * Vai trò mặc định được cấp cho liên kết chia sẻ mới &mdash;
     * {@link ShareLink.Role#VIEWER} (chỉ xem).
     *
     * @return vai trò mặc định.
     */
    @Override
    public ShareLink.Role defaultRole() {
        return ShareLink.Role.VIEWER;
    }
}