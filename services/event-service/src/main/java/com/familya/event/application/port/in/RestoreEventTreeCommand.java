package com.familya.event.application.port.in;

import java.util.UUID;

/**
 * Lệnh <b>khôi phục</b> (compensating) cho Saga xóa cây — hủy tombstone
 * cho mọi sự kiện trong cây đã bị purge trước đó.
 *
 * <p>Use case xử lý:
 * {@link com.familya.event.application.usecase.RestoreEventTreeUseCase}.
 *
 * <p>Đây là bước bù (compensation) trong Saga; nó được thực hiện khi
 * một bước khác trong Saga thất bại và cần rollback.
 *
 * @param operationId định danh toàn Saga.
 * @param treeId      định danh cây cần khôi phục.
 *
 * @author gia-pha platform
 */
public record RestoreEventTreeCommand(
        UUID operationId,
        UUID treeId) {
}
