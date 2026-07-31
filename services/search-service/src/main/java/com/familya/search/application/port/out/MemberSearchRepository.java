package com.familya.search.application.port.out;

import com.familya.search.application.port.in.SearchMembersCommand;
import com.familya.search.domain.model.MemberSearchDocument;

import java.util.List;
import java.util.UUID;

/**
 * Cổng (port) truy vấn các tài liệu tìm kiếm thành viên đã được chiếu vào
 * bảng {@code search_member_doc}.
 */
public interface MemberSearchRepository {

    /**
     * Tìm các tài liệu thành viên khớp bộ lọc và chuỗi truy vấn (đã chuẩn hoá).
     *
     * @param filter          bộ lọc (năm sinh, khoảng năm sinh, tombstoned) hoặc {@code null}.
     * @param normalizedQuery chuỗi truy vấn đã chuẩn hoá; rỗng/blank thì bỏ qua LIKE.
     * @param treeId          định danh cây gia phả.
     * @param limit           số kết quả tối đa.
     * @return danh sách tài liệu khớp, sắp xếp theo thời điểm cập nhật giảm dần.
     */
    List<MemberSearchDocument> search(SearchMembersCommand.MemberFilter filter,
                                      String normalizedQuery,
                                      UUID treeId,
                                      int limit);

    /**
     * Xoá toàn bộ tài liệu thành viên của một cây. Triển khai mặc định trả về
     * {@code 0L} để giữ tương thích nhị phân; triển khai JDBC sẽ override
     * bằng câu lệnh DELETE thực sự.
     *
     * @param treeId định danh cây cần xoá.
     * @return số bản ghi đã xoá.
     */
    default long deleteByTree(UUID treeId) { return 0L; }
}