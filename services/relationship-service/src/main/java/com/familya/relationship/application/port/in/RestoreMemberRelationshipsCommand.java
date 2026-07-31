package com.familya.relationship.application.port.in;

import java.util.UUID;

/**
 * Lệnh yêu cầu khôi phục (bỏ tombstone) các quan hệ đã bị vô hiệu hóa trước
 * đó bởi use case {@code DisableMemberRelationshipsUseCase} trong saga xóa
 * thành viên.
 * <p>
 * Đây là <b>bước bồi thường (compensation step)</b> của saga: nếu bước
 * disable thất bại hoặc cần phải undo do một bước khác của saga thất bại,
 * orchestrator sẽ phát lệnh này. Use case {@code RestoreMemberRelationshipsUseCase}
 * sẽ đọc lại snapshot bồi thường đã lưu theo {@code operationId} và gọi
 * {@code Repository.untombstone} cho từng cạnh.
 * </p>
 *
 * @param operationId định danh thao tác saga, dùng để tra snapshot bồi thường
 * @param treeId      định danh cây gia phả chứa các quan hệ cần khôi phục
 * @param memberId    định danh thành viên đã bị xóa (tham khảo, phục vụ log/audit)
 */
public record RestoreMemberRelationshipsCommand(
        UUID operationId,
        UUID treeId,
        UUID memberId) {
}