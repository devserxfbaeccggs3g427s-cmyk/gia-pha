package com.familya.relationship.application.port.in;

import java.util.UUID;

/**
 * Lệnh yêu cầu <b>khôi phục</b> các quan hệ đã bị tombstone bởi saga xóa cây
 * ({@code PurgeRelationshipTreeUseCase}). Đây là bước bồi thường (compensation)
 * của saga xóa cây.
 * <p>
 * Quy trình khôi phục:
 * </p>
 * <ol>
 *   <li>Use case đọc snapshot bồi thường đã lưu theo {@link #operationId}.</li>
 *   <li>Đối với mỗi quan hệ trong snapshot, gọi {@code untombstone} để đưa
 *       cạnh về trạng thái sống, đồng thời tăng version.</li>
 *   <li>Trả về số cạnh đã khôi phục cùng phiên bản aggregate & epoch đã áp
 *       dụng để orchestrator xác nhận barrier.</li>
 * </ol>
 *
 * @param operationId định danh thao tác saga (khóa của snapshot)
 * @param treeId      định danh cây gia phả cần khôi phục
 */
public record RestoreRelationshipTreeCommand(
        UUID operationId,
        UUID treeId) {
}