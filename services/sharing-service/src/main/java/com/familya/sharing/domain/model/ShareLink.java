package com.familya.sharing.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Đại diện cho một liên kết chia sẻ (share link) trong hệ thống gia phả.
 * <p>
 * Một {@code ShareLink} là bản ghi miền (domain record) bất biến được lưu trữ
 * trong cơ sở dữ liệu và được sử dụng để cấp quyền truy cập công khai vào một
 * phần dữ liệu của cây gia phả (tree). Mỗi liên kết được định danh bằng một
 * {@code UUID} duy nhất và chỉ lưu trữ <b>hash</b> của token (không lưu token
 * thô) nhằm bảo đảm tính bảo mật: ngay cả khi cơ sở dữ liệu bị lộ, kẻ tấn công
 * cũng không thể sử dụng token để truy cập.
 * <p>
 * Liên kết có thể ở một trong hai trạng thái vòng đời:
 * <ul>
 *     <li><b>Đang hoạt động</b> &mdash; {@code revokedAt == null} và
 *         {@code expiresAt} có thể {@code null} hoặc ở tương lai.</li>
 *     <li><b>Đã thu hồi</b> &mdash; {@code revokedAt != null}. Lý do thu hồi
 *         được lưu trong {@code revocationReason}.</li>
 * </ul>
 *
 * @param id                  định danh duy nhất của liên kết chia sẻ.
 * @param treeId              định danh cây gia phả mà liên kết đang chia sẻ.
 * @param scope               phạm vi chia sẻ (cả cây, một thành viên, một media hay một sự kiện).
 * @param targetId            định danh của đối tượng cụ thể trong phạm vi chia sẻ
 *                            (có thể {@code null} khi {@code scope == TREE}).
 * @param role                vai trò được cấp cho người dùng cuối khi sử dụng liên kết.
 * @param tokenHash           giá trị băm SHA-256 (dạng hex) của token gốc, dùng để tra cứu.
 * @param createdByUserId     định danh người dùng đã tạo liên kết.
 * @param createdAt           thời điểm liên kết được tạo.
 * @param expiresAt           thời điểm hết hạn (có thể {@code null} nếu không giới hạn).
 * @param revokedAt           thời điểm thu hồi (có thể {@code null} nếu chưa thu hồi).
 * @param revocationReason    lý do thu hồi (ví dụ: do người dùng yêu cầu, do xóa cây, v.v.).
 * @param revision            phiên bản logic dùng cho optimistic concurrency control.
 * @param version             phiên bản cập nhật của bản ghi, tăng mỗi khi liên kết thay đổi.
 *
 * @author gia-pha platform team
 */
public record ShareLink(
        UUID id,
        UUID treeId,
        Scope scope,
        UUID targetId,
        Role role,
        String tokenHash,
        UUID createdByUserId,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt,
        String revocationReason,
        long revision,
        long version
) {
    /**
     * Phạm vi chia sẻ của liên kết &mdash; xác định loại dữ liệu nào trong cây
     * gia phả mà liên kết được phép truy cập.
     */
    public enum Scope { TREE, MEMBER, MEDIA, EVENT }

    /**
     * Vai trò (role) được cấp cho người dùng cuối khi họ sử dụng liên kết chia sẻ.
     * Vai trò quyết định mức độ thao tác được phép (xem, đóng góp, chỉnh sửa, quản trị).
     */
    public enum Role { VIEWER, CONTRIBUTOR, EDITOR, ADMIN }
}