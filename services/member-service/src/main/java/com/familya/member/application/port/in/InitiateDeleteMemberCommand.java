package com.familya.member.application.port.in;

import java.util.UUID;

/**
 * Initiating input to the delete-member Saga. The Member service owns this
 * Saga (ADR-003); authorization is captured here and carried in the envelope
 * so participants do not impersonate the user.
 */
/**
 * Đầu vào khởi tạo Saga xóa thành viên. Member Service sở hữu Saga này (theo ADR-003);
 * thông tin ủy quyền được đóng gói trong command và được phát đi cùng envelope để các
 * participant không thể mạo danh người dùng.
 *
 * @param treeId               mã cây
 * @param memberId             mã thành viên cần xóa
 * @param actingUser           người dùng thực hiện
 * @param expectedMemberVersion phiên bản kỳ vọng của thành viên (optimistic concurrency)
 * @param expectedTreeRevision phiên bản kỳ vọng của cây
 * @param expectedTreeEpoch    epoch kỳ vọng của cây
 */
public record InitiateDeleteMemberCommand(
        UUID treeId,
        UUID memberId,
        UUID actingUser,
        long expectedMemberVersion,
        long expectedTreeRevision,
        long expectedTreeEpoch) {
}