package com.familya.search.application.port.out;

import com.familya.search.domain.model.ReportSnapshot;

import java.util.Optional;
import java.util.UUID;

/**
 * Cổng (port) lưu trữ và truy vấn các bản chụp báo cáo
 * (xem {@link ReportSnapshot}).
 */
public interface ReportRepository {

    /**
     * Lưu (ghi đè nếu đã tồn tại) một bản chụp báo cáo.
     *
     * @param snapshot bản chụp cần lưu.
     */
    void save(ReportSnapshot snapshot);

    /**
     * Tìm một bản chụp báo cáo theo {@code id}.
     *
     * @param reportId định danh bản chụp.
     * @return {@code Optional} chứa bản chụp nếu tồn tại, ngược lại rỗng.
     */
    Optional<ReportSnapshot> findById(UUID reportId);

    /**
     * Kiểm tra xem đã có bản chụp báo cáo cho {@code kind} tại mức watermark
     * {@code >= watermark} chưa - dùng để tránh tính lặp lại cùng một báo cáo.
     *
     * @param treeId    định danh cây gia phả.
     * @param kind      loại báo cáo.
     * @param watermark mức watermark tối thiểu.
     * @return {@code true} nếu đã có bản chụp dùng được.
     */
    boolean exists(UUID treeId, ReportSnapshot.Kind kind, long watermark);

    /**
     * Xoá toàn bộ bản chụp báo cáo của một cây. Triển khai mặc định trả về
     * {@code 0L} để giữ tương thích nhị phân.
     *
     * @param treeId định danh cây cần xoá.
     * @return số bản ghi đã xoá.
     */
    default long deleteByTree(UUID treeId) { return 0L; }
}