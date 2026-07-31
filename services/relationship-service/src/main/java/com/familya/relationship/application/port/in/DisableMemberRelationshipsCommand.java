package com.familya.relationship.application.port.in;

import java.util.UUID;

/**
 * Lệnh đến từ saga xóa thành viên của {@code Member service}, yêu cầu
 * Relationship service <b>vô hiệu hóa</b> (tombstone) mọi cạnh đang hoạt động
 * có liên quan tới một thành viên.
 *
 * <h2>Hành vi bắt buộc</h2>
 * <ul>
 *   <li>Tombstone mọi cạnh đang hoạt động mà {@code fromMemberId} hoặc
 *       {@code toMemberId} trùng với {@link #memberId}.</li>
 *   <li>Lưu <b>snapshot bồi thường</b> (compensation snapshot) cho phép khôi
 *       phục sau này, khóa theo {@link #operationId}.</li>
 *   <li>Trả về phiên bản aggregate và epoch đã áp dụng để orchestrator kiểm
 *       tra barrier (rào chắn) quyết định commit/rollback saga.</li>
 * </ul>
 *
 * @param operationId            định danh thao tác saga (dùng để đánh dấu snapshot)
 * @param treeId                 định danh cây gia phả
 * @param memberId               định danh thành viên cần vô hiệu hóa quan hệ
 * @param expectedAggregateVersion phiên bản aggregate mà orchestrator cho là hiện hành
 * @param expectedEpoch          epoch hiện tại mà orchestrator cho là hiện hành
 * @param targetAggregateVersion phiên bản aggregate mục tiêu sau khi hoàn tất
 * @param targetEpoch            epoch mục tiêu sau khi hoàn tất
 */
public record DisableMemberRelationshipsCommand(
        UUID operationId,
        UUID treeId,
        UUID memberId,
        long expectedAggregateVersion,
        long expectedEpoch,
        long targetAggregateVersion,
        long targetEpoch) {
}