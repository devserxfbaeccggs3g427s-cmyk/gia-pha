package com.familya.event.application.port.in;

import java.util.UUID;

/**
 * Lệnh tombstone <b>hàng loạt</b> mọi sự kiện còn sống trong một cây
 * gia phả — đây là bước tham gia của <b>event-service</b> trong Saga
 * xóa cây (delete-tree Saga).
 *
 * <p>Use case xử lý:
 * {@link com.familya.event.application.usecase.PurgeEventTreeUseCase}.
 *
 * <h2>Tham số Saga</h2>
 * <ul>
 *   <li>{@code operationId}: định danh toàn Saga (dùng để truy vết).</li>
 *   <li>{@code targetAggregateVersion}: phiên bản kỳ vọng của cây — sẽ
 *       được áp dụng cho kết quả trả về.</li>
 *   <li>{@code targetEpoch}: epoch phục vụ cho đồng bộ chéo dịch vụ.</li>
 * </ul>
 *
 * @param operationId            định danh toàn Saga.
 * @param treeId                 định danh cây cần purge.
 * @param targetAggregateVersion phiên bản mục tiêu.
 * @param targetEpoch            epoch mục tiêu.
 *
 * @author gia-pha platform
 */
public record PurgeEventTreeCommand(
        UUID operationId,
        UUID treeId,
        long targetAggregateVersion,
        long targetEpoch) {
}
