package com.familya.relationship.application.port.in;

import java.util.UUID;

/**
 * Lệnh đến từ saga xóa cây của {@code Tree Access service}, yêu cầu Relationship
 * service đánh dấu xóa mềm <b>mọi quan hệ chưa tombstone</b> trong một cây.
 *
 * <h2>Hành vi bắt buộc</h2>
 * <ul>
 *   <li>Duyệt tất cả quan hệ đang hoạt động trong cây và tombstone theo lô.</li>
 *   <li>Lưu <b>snapshot bồi thường</b> cho phép khôi phục cây sau này.</li>
 *   <li>Trả về phiên bản aggregate và epoch áp dụng để orchestrator kiểm tra
 *       barrier quyết định commit/rollback.</li>
 * </ul>
 *
 * @param operationId            định danh thao tác saga (khóa của snapshot)
 * @param treeId                 định danh cây gia phả cần thanh lọc
 * @param targetAggregateVersion phiên bản aggregate mục tiêu
 * @param targetEpoch            epoch mục tiêu
 */
public record PurgeRelationshipTreeCommand(
        UUID operationId,
        UUID treeId,
        long targetAggregateVersion,
        long targetEpoch) {
}