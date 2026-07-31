package com.familya.search.application.port.out;

import com.familya.search.domain.model.AutocompleteEntry;

import java.util.List;
import java.util.UUID;

/**
 * Cổng (port) ra phía persistence cho bảng gợi ý nhanh (autocomplete).
 *
 * <p>Triển khai JDBC đọc trực tiếp từ bảng {@code autocomplete_entry} và đối
 * chiếu tiền tố đã được chuẩn hoá. Service không sửa bảng này trực tiếp -
 * việc ghi được thực hiện bởi {@code SearchProjectionConsumer} khi nhận sự
 * kiện member/event/media.</p>
 */
public interface AutocompleteRepository {

    /**
     * Lấy danh sách gợi ý khớp tiền tố đã chuẩn hoá trong phạm vi một cây.
     *
     * @param normalizedPrefix tiền tố đã được {@code VietnameseNormalizer} chuẩn hoá.
     * @param treeId           định danh cây gia phả cần truy vấn.
     * @param limit            số kết quả tối đa.
     * @return danh sách gợi ý sắp xếp theo trọng số giảm dần.
     */
    List<AutocompleteEntry> suggestions(String normalizedPrefix, UUID treeId, int limit);

    /**
     * Xoá toàn bộ gợi ý của một cây (dùng trong bước PURGE của Saga).
     * Triển khai mặc định trả về {@code 0L} để giữ tương thích nhị phân.
     *
     * @param treeId định danh cây gia phả cần xoá.
     * @return số bản ghi đã bị xoá.
     */
    default long deleteByTree(UUID treeId) { return 0L; }
}