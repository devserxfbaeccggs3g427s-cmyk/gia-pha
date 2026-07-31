package com.familya.search.application.port.out;

import com.familya.search.application.port.in.SearchEventsCommand;
import com.familya.search.domain.model.EventSearchDocument;

import java.util.List;
import java.util.UUID;

/**
 * Cổng (port) truy vấn các tài liệu tìm kiếm sự kiện đã được chiếu vào bảng
 * {@code search_event_doc}.
 */
public interface EventSearchRepository {

    /**
     * Tìm các tài liệu sự kiện khớp bộ lọc và chuỗi truy vấn (đã chuẩn hoá)
     * trong phạm vi một cây.
     *
     * @param filter          bộ lọc bổ sung (loại, khoảng ngày, tombstoned) hoặc {@code null}.
     * @param normalizedQuery chuỗi truy vấn đã được chuẩn hoá; nếu rỗng/blank
     *                        thì bỏ qua điều kiện LIKE.
     * @param treeId          định danh cây gia phả.
     * @param limit           số kết quả tối đa.
     * @return danh sách tài liệu khớp, sắp xếp theo thời điểm cập nhật giảm dần.
     */
    List<EventSearchDocument> search(SearchEventsCommand.EventFilter filter,
                                     String normalizedQuery,
                                     UUID treeId,
                                     int limit);

    /**
     * Xoá toàn bộ tài liệu sự kiện của một cây. Triển khai mặc định trả về
     * {@code 0L} để giữ tương thích nhị phân.
     *
     * @param treeId định danh cây cần xoá.
     * @return số bản ghi đã xoá.
     */
    default long deleteByTree(UUID treeId) { return 0L; }
}