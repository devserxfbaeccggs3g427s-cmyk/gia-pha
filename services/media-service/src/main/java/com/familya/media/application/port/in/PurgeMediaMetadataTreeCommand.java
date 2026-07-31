package com.familya.media.application.port.in;

import java.util.UUID;

/**
 * Lệnh (command) đầu vào cho use case {@code PurgeMediaMetadataTreeUseCase}.
 *
 * <p>Đây là {@code port in} của kiến trúc hexagonal, được {@code Member}
 * Saga gửi tới media-service khi một family-tree bị xóa mềm (purge).
 * Use case sẽ:</p>
 * <ol>
 *   <li>Đánh dấu tombstone (xóa mềm) mọi {@code MediaAsset} thuộc
 *       {@link #treeId()}.</li>
 *   <li>Nếu {@link #placeRetentionHolds()} là {@code true}, đặt retention
 *       hold 30 ngày lên toàn bộ media trong tree để cleanup worker chỉ
 *       xóa binary sau khi hết hạn.</li>
 *   <li>Xóa mọi tham chiếu media (loại {@code MEMBER}, {@code EVENT}) trỏ
 *       tới target thuộc tree.</li>
 * </ol>
 *
 * <p>Binary vật lý KHÔNG bị xóa ngay; việc đó thuộc trách nhiệm của
 * delayed cleanup worker.</p>
 *
 * @param operationId           UUID định danh Saga; dùng cho retention
 *                              reason và truy vết rollback.
 * @param treeId                UUID family-tree cần purge metadata.
 * @param placeRetentionHolds   {@code true} nếu muốn giữ binary trong
 *                              grace period (mặc định của delete-tree
 *                              Saga); {@code false} cho phép cleanup xóa
 *                              binary ngay khi tombstone xong.
 * @param targetAggregateVersion phiên bản aggregate dự kiến tại thời
 *                              điểm Saga commit.
 * @param targetEpoch           epoch của Saga; phân biệt các lần chạy
 *                              lại cùng {@code operationId}.
 */
public record PurgeMediaMetadataTreeCommand(
        UUID operationId,
        UUID treeId,
        boolean placeRetentionHolds,
        long targetAggregateVersion,
        long targetEpoch) {
}