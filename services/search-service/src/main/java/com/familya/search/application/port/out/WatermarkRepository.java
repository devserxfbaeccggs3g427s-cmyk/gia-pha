package com.familya.search.application.port.out;

import com.familya.search.domain.model.RevisionBarrier;
import com.familya.search.domain.model.Watermark;

import java.util.Optional;
import java.util.UUID;

/**
 * Cổng (port) đọc/ghi watermark (phiên bản chung, dùng ở nhiều nơi trong
 * service search). Đây là interface "đơn giản" so với {@code SearchWatermarkRepository}
 * - không có các phương thức mặc định cho Saga.
 */
public interface WatermarkRepository {

    /**
     * Tìm watermark cho một miền cụ thể của cây.
     *
     * @param treeId định danh cây gia phả.
     * @param domain miền dữ liệu.
     * @return {@code Optional} chứa {@link Watermark} nếu có, ngược lại rỗng.
     */
    Optional<Watermark> find(UUID treeId, Watermark.Domain domain);

    /**
     * Lấy barrier (rào chắn) phiên bản của cây.
     *
     * @param treeId định danh cây gia phả.
     * @return {@link RevisionBarrier} chứa watermark của mọi miền.
     */
    RevisionBarrier barrierFor(UUID treeId);

    /**
     * Nâng (hoặc giữ nguyên) watermark của một miền lên {@code value}.
     *
     * @param treeId định danh cây gia phả.
     * @param domain miền dữ liệu.
     * @param value  giá trị watermark mới.
     */
    void advance(UUID treeId, Watermark.Domain domain, long value);
}
