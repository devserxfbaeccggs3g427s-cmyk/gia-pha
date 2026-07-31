package com.familya.search.application.port.out;

import com.familya.search.application.port.in.SearchMediaCommand;
import com.familya.search.domain.model.MediaSearchDocument;

import java.util.List;
import java.util.UUID;

/**
 * Cổng (port) truy vấn các tài liệu tìm kiếm media đã được chiếu vào bảng
 * {@code search_media_doc}.
 */
public interface MediaSearchRepository {

    /**
     * Tìm các tài liệu media khớp bộ lọc và chuỗi truy vấn (đã chuẩn hoá).
     *
     * @param filter          bộ lọc (loại media, tombstoned) hoặc {@code null}.
     * @param normalizedQuery chuỗi truy vấn đã chuẩn hoá; rỗng/blank thì bỏ qua LIKE.
     * @param treeId          định danh cây gia phả.
     * @param limit           số kết quả tối đa.
     * @return danh sách tài liệu khớp, sắp xếp theo thời điểm cập nhật giảm dần.
     */
    List<MediaSearchDocument> search(SearchMediaCommand.MediaFilter filter,
                                      String normalizedQuery,
                                      UUID treeId,
                                      int limit);

    /**
     * Xoá toàn bộ tài liệu media của một cây. Triển khai mặc định trả về
     * {@code 0L} để giữ tương thích nhị phân.
     *
     * @param treeId định danh cây cần xoá.
     * @return số bản ghi đã xoá.
     */
    default long deleteByTree(UUID treeId) { return 0L; }
}