package com.familya.search.application.port.out;

import com.familya.search.domain.model.StatisticsSnapshot;

import java.util.Optional;
import java.util.UUID;

/**
 * Cổng (port) tính toán và truy vấn thống kê cây gia phả.
 */
public interface StatisticsRepository {

    /**
     * Tính (hoặc tính lại) bản thống kê cho cây tại mức watermark chỉ định
     * và ghi nhớ vào bảng {@code statistics_snapshot}.
     *
     * @param treeId    định danh cây gia phả.
     * @param watermark mức watermark mà bản thống kê phản ánh.
     * @return bản thống kê vừa được tính.
     */
    StatisticsSnapshot compute(UUID treeId, long watermark);

    /**
     * Lấy bản thống kê mới nhất đã lưu cho cây.
     *
     * @param treeId định danh cây gia phả.
     * @return {@code Optional} chứa bản thống kê, hoặc rỗng nếu chưa từng tính.
     */
    Optional<StatisticsSnapshot> latest(UUID treeId);

    /**
     * Xoá toàn bộ bản thống kê của cây. Triển khai mặc định trả về
     * {@code 0L} để giữ tương thích nhị phân.
     *
     * @param treeId định danh cây cần xoá.
     * @return số bản ghi đã xoá.
     */
    default long deleteByTree(UUID treeId) { return 0L; }
}