package com.familya.event.application.port.out;

import com.familya.event.domain.model.DomainEvent;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cổng ra (output port) chịu trách nhiệm <b>truy xuất và lưu trữ</b>
 * các {@link DomainEvent aggregate DomainEvent} trong kho lưu trữ vĩnh
 * viễn (MySQL/JDBC).
 *
 * <p>Đây là cổng giao tiếp duy nhất giữa tầng application và tầng
 * adapter persistence, đảm bảo Domain không phụ thuộc vào công nghệ lưu
 * trữ cụ thể (Dependency Inversion).
 *
 * <h2>Triển khai</h2>
 * <ul>
 *   <li>{@link com.familya.event.adapter.out.persistence.JdbcEventRepository}
 *       — triển khai JDBC dùng {@code NamedParameterJdbcTemplate}.</li>
 * </ul>
 *
 * <h2>Các phương thức mặc định (default)</h2>
 * <p>Một số phương thức có thân mặc định để giữ khả năng tương thích
 * nhị phân khi bổ sung cổng — giao diện có thể phát triển mà không
 * phá vỡ các adapter cũ.
 *
 * @author gia-pha platform
 */
public interface EventRepository {

    /**
     * Chèn một aggregate mới vào cơ sở dữ liệu.
     *
     * @param event aggregate cần chèn.
     * @throws org.springframework.dao.DuplicateKeyException nếu trùng khóa chính.
     */
    void insert(DomainEvent event);

    /**
     * Tra cứu aggregate theo ID.
     *
     * @param id định danh duy nhất.
     * @return {@link Optional} chứa aggregate nếu tồn tại.
     */
    Optional<DomainEvent> findById(UUID id);

    /**
     * Liệt kê các sự kiện trong một cây gia phả.
     *
     * @param treeId             định danh cây.
     * @param includeTombstoned  {@code true} để bao gồm cả sự kiện đã tombstone.
     * @return danh sách aggregate (rỗng nếu không có).
     */
    List<DomainEvent> listByTree(UUID treeId, boolean includeTombstoned);

    /**
     * Cập nhật aggregate đã tồn tại. Kỳ vọng {@code version} đã được
     * tăng và {@code expectedVersion} đã khớp (caller đã kiểm tra).
     *
     * @param event aggregate với phiên bản mới.
     */
    void update(DomainEvent event);

    /**
     * Liệt kê các sự kiện <b>chưa tombstone</b> của một cây mà có
     * tham chiếu tới {@code memberId} (ở cả {@code primary} và
     * {@code additional}).
     *
     * @param treeId   định danh cây.
     * @param memberId thành viên cần tra cứu.
     * @return danh sách các sự kiện liên quan.
     */
    List<DomainEvent> listReferencingMember(UUID treeId, UUID memberId);

    /**
     * Lưu một <b>snapshot bù (compensation)</b> cho Saga xóa thành viên.
     * Thường chứa các tham chiếu cũ để có thể khôi phục khi rollback.
     *
     * <p>Triển khai phải đảm bảo <i>best-effort idempotent</i>: việc ghi
     * trùng cùng {@code operationId} không gây lỗi.
     *
     * @param operationId  định danh Saga.
     * @param snapshotJson nội dung snapshot (chuỗi JSON).
     */
    void saveCompensationSnapshot(UUID operationId, String snapshotJson);

    /**
     * Đọc snapshot bù đã lưu trước đó.
     *
     * @param operationId định danh Saga.
     * @return chuỗi JSON hoặc {@code null} nếu không tồn tại.
     */
    String loadCompensationSnapshot(UUID operationId);

    /**
     * Khôi phục các tham chiếu thành viên đã bị gỡ bởi Saga delete-member.
     *
     * <p>Triển khai mặc định trả về {@code 0} để giữ khả năng tương thích
     * nhị phân — triển khai thực sự (ví dụ JDBC) nên override.
     *
     * @param operationId định danh Saga.
     * @param memberId    thành viên cần khôi phục tham chiếu.
     * @return số sự kiện đã được cập nhật.
     */
    default int restoreMemberReferences(UUID operationId, UUID memberId) { return 0; }

    /**
     * Tombstone hàng loạt mọi sự kiện <b>chưa tombstone</b> trong cây
     * — dùng cho bước {@code PURGE_EVENT_TREE} của Saga xóa cây.
     *
     * <p>Triển khai mặc định duyệt {@link #listByTree(UUID, boolean)}
     * với {@code includeTombstoned=false} và gọi
     * {@link DomainEvent#tombstone(long, Instant)} cho mỗi phần tử; triển
     * khai hiệu năng cao (JDBC batch) nên override.
     *
     * @param treeId cây cần purge.
     * @param at    thời điểm tombstone.
     * @return số lượng sự kiện đã được đánh tombstone.
     */
    default int bulkTombstoneByTree(UUID treeId, Instant at) {
        // Triển khai mặc định: tuần tự, an toàn nhưng có thể chậm với cây
        // có hàng nghìn sự kiện. JDBC adapter nên override bằng batch update.
        int n = 0;
        for (DomainEvent ev : listByTree(treeId, false)) {
            ev.tombstone(ev.version(), at);
            update(ev);
            n++;
        }
        return n;
    }
}
