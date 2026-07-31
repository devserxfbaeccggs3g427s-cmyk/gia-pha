package com.familya.treeaccess.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Migration loader input. Each tree manifest carries immutable IDs and
 * timestamps; the loader is idempotent per treeId. Used by the
 * Migration Service during the legacy cutover.
 */
/**
 * Lệnh nạp manifest cây trong quá trình migration. Với mỗi {@code treeId},
 * việc nạp là idempotent: chạy lại không sinh bản ghi trùng.
 *
 * @param treeId          mã cây (bất biến)
 * @param ownerUserId     UUID chủ sở hữu
 * @param name            tên cây
 * @param initialRevision revision ban đầu
 * @param initialEpoch    epoch ban đầu
 * @param createdAt       thời điểm tạo cây
 * @param memberships     danh sách thành viên đi kèm manifest
 * @param replaySafe      nếu {@code true}, bỏ qua trong trường hợp manifest đã tồn tại
 */
public record LoadTreeManifestCommand(
        UUID treeId,
        UUID ownerUserId,
        String name,
        long initialRevision,
        long initialEpoch,
        Instant createdAt,
        List<MembershipLine> memberships,
        boolean replaySafe
) {
    /**
     * Một dòng membership trong manifest — biểu diễn dữ liệu lịch sử
     * được migration service xuất ra.
     *
     * @param userId    UUID người dùng
     * @param role      vai trò tại thời điểm nạp
     * @param grantedBy UUID người đã cấp quyền
     * @param grantedAt thời điểm cấp quyền
     */
    public record MembershipLine(UUID userId, String role, UUID grantedBy, Instant grantedAt) { }
}