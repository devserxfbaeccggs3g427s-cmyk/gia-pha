package com.familya.search.application.port.out;

import com.familya.search.domain.model.RevisionBarrier;
import com.familya.search.domain.model.Watermark;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Cổng (port) đọc/ghi watermark của search service.
 *
 * <p>Đây là phiên bản riêng của {@code WatermarkRepository} - có thêm các
 * phương thức mặc định (default) hỗ trợ việc nâng watermark hàng loạt và
 * khôi phục phục vụ Saga.</p>
 */
public interface SearchWatermarkRepository {

    /**
     * Tìm watermark cho một miền cụ thể trong một cây.
     *
     * @param treeId định danh cây gia phả.
     * @param domain miền dữ liệu.
     * @return {@code Optional} chứa {@link Watermark} nếu tồn tại, ngược lại rỗng.
     */
    Optional<Watermark> find(UUID treeId, Watermark.Domain domain);

    /**
     * Lấy barrier (rào chắn) phiên bản của cây - bản đồ từ miền sang giá trị watermark.
     *
     * @param treeId định danh cây gia phả.
     * @return {@link RevisionBarrier} chứa tất cả watermark hiện tại của cây.
     */
    RevisionBarrier barrierFor(UUID treeId);

    /**
     * Nâng (hoặc giữ nguyên) watermark của một miền lên giá trị {@code value}.
     *
     * @param treeId định danh cây gia phả.
     * @param domain miền dữ liệu.
     * @param value  giá trị watermark mới (chỉ tiến lên, không lùi).
     */
    void advance(UUID treeId, Watermark.Domain domain, long value);

    /**
     * Nâng watermark cho mọi miền của cây lên {@code aggregateVersion}. Triển
     * khai mặc định duyệt qua tất cả {@link Watermark.Domain} và gọi
     * {@link #advance(UUID, Watermark.Domain, long)}.
     *
     * @param treeId            định danh cây gia phả.
     * @param aggregateVersion  giá trị watermark mới.
     * @param epoch             epoch tương ứng (tham số giữ chỗ cho tương lai).
     * @param now               mốc thời gian hiện tại.
     */
    default void advance(UUID treeId, long aggregateVersion, long epoch, Instant now) {
        for (Watermark.Domain d : Watermark.Domain.values()) {
            advance(treeId, d, aggregateVersion);
        }
    }

    /**
     * Buộc mọi watermark của cây về giá trị mới để lần đọc kế tiếp tái dựng
     * projection từ luồng sự kiện nguồn chân lý. Được {@code RestoreSearchTreeUseCase}
     * dùng khi Saga xoá cây cần bù trừ. Triển khai mặc định là không-op.
     *
     * @param treeId định danh cây gia phả.
     * @param now   mốc thời gian hiện tại.
     */
    default void refreshForRestore(UUID treeId, Instant now) { /* no-op */ }
}