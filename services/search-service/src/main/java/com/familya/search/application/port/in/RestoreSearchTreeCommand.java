package com.familya.search.application.port.in;

import java.util.UUID;

/**
 * Lệnh (command) yêu cầu khôi phục projection sau khi Saga xoá cây thất bại.
 *
 * <p>Trong Saga, nếu bước {@code PURGE_SEARCH_TREE} hoặc một bước khác thất
 * bại, Saga sẽ chạy các bước bù trừ (compensation). Lệnh này ra lệnh cho
 * service search "buông" watermark về mức cho phép projection được tái
 * dựng từ sự kiện nguồn (source-of-truth).</p>
 *
 * @param operationId định danh thao tác Saga (dùng để log và truy vết).
 * @param treeId      định danh cây gia phả cần khôi phục.
 */
public record RestoreSearchTreeCommand(
        UUID operationId,
        UUID treeId) {
}