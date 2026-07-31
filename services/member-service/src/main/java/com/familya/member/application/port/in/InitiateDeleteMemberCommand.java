package com.familya.member.application.port.in;

import java.util.UUID;

/**
 * Đầu vào khởi tạo Saga xóa thành viên. Member Service sở hữu Saga này (theo ADR-003);
 * thông tin ủy quyền được đóng gói trong command và được phát đi cùng envelope để các
 * participant không thể mạo danh người dùng.
 *
 * <p>Idempotency-Key và traceparent giúp reserve-or-replay (Task 13.1) và W3C
 * trace propagation. Cả hai đều có thể null khi client không cung cấp; hành vi
 * lúc đó vẫn tương thích với caller cũ nhưng kém đảm bảo hơn.</p>
 */
public record InitiateDeleteMemberCommand(
        UUID treeId,
        UUID memberId,
        UUID actingUser,
        long expectedMemberVersion,
        long expectedTreeRevision,
        long expectedTreeEpoch,
        String idempotencyKey,
        String traceparent) {
}
