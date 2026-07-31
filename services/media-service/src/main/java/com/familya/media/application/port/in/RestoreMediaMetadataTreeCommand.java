package com.familya.media.application.port.in;

import java.util.UUID;

/**
 * Lệnh (command) đầu vào cho use case {@code RestoreMediaMetadataTreeUseCase}.
 *
 * <p>Đây là {@code port in} của kiến trúc hexagonal, dùng trong bước bù
 * trừ (compensation) của delete-tree Saga khi Saga fail trước ranh giới
 * không-thể-đảo (irreversible boundary). Use case sẽ:</p>
 * <ol>
 *   <li>Quét toàn bộ {@code MediaAsset} của {@link #treeId()} (bao gồm cả
 *       các bản ghi đã tombstone).</li>
 *   <li>Với mỗi media đang tombstone: giải phóng retention hold (nếu có)
 *       và clear {@code tombstonedAt} để media trở lại hoạt động.</li>
 * </ol>
 *
 * @param operationId UUID định danh Saga cần bù trừ; dùng để giải phóng
 *                    đúng các retention hold đã đặt bởi bước purge.
 * @param treeId      UUID family-tree đang được khôi phục.
 */
public record RestoreMediaMetadataTreeCommand(
        UUID operationId,
        UUID treeId) {
}