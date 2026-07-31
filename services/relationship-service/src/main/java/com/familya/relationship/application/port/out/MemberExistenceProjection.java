package com.familya.relationship.application.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Cổng truy vấn projection tồn tại của thành viên (do Member service cung cấp).
 * <p>
 * Relationship service không sở hữu thực thể "thành viên"; thay vào đó nó chỉ
 * lưu trữ các opaque ID và phải dựa vào projection để biết một thành viên có
 * còn tồn tại và chưa bị tombstone hay không. Projection này được xây dựng từ
 * Kafka stream {@code member.events.v1} (xem {@code ProjectionConsumer.onMember}).
 * </p>
 *
 * <h2>Lý do phải kiểm tra</h2>
 * <p>
 * Nếu Relationship service cho phép tạo cạnh trỏ vào thành viên đã bị xóa
 * (dangling reference), các thuật toán đồ thị có thể tạo ra kết quả vô nghĩa
 * và projection tổng hợp sẽ chứa tham chiếu treo. Vì vậy, <b>mọi</b> lệnh tạo
 * cạnh đều phải thông qua {@link #isAvailable} trước khi chèn.
 * </p>
 */
public interface MemberExistenceProjection {

    /**
     * Kiểm tra thành viên có "khả dụng" hay không.
     * <p>
     * "Khả dụng" nghĩa là: tồn tại trong Member service và chưa bị tombstone.
     * </p>
     *
     * @param treeId   định danh cây gia phả
     * @param memberId định danh thành viên
     * @return {@code true} nếu thành viên đang tồn tại và chưa bị tombstone
     */
    boolean isAvailable(UUID treeId, UUID memberId);

    /**
     * Trả về trạng thái tombstone của thành viên (nếu biết).
     * <p>
     * Phân biệt giữa ba trạng thái:
     * </p>
     * <ul>
     *   <li>{@code Optional.empty()}: projection chưa có dòng nào cho thành viên.</li>
     *   <li>{@code Optional.of(false)}: thành viên tồn tại và chưa bị tombstone.</li>
     *   <li>{@code Optional.of(true)}: thành viên đã bị tombstone (dangling).</li>
     * </ul>
     *
     * @param treeId   định danh cây gia phả
     * @param memberId định danh thành viên
     * @return {@code Optional<Boolean>} mô tả trạng thái tombstone
     */
    Optional<Boolean> tombstone(UUID treeId, UUID memberId);
}