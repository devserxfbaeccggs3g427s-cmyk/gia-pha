package com.familya.event.application.usecase;

import com.familya.event.application.port.out.EventRepository;
import com.familya.event.application.port.out.ReferenceAvailability;
import com.familya.event.domain.model.DomainEvent;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Use case <b>truy vấn</b> (read side) — cung cấp các thao tác đọc
 * aggregate {@link DomainEvent} và cảnh báo tham chiếu đứt.
 *
 * <p>Lớp này đóng vai trò <i>Query side</i> trong mô hình CQRS:
 * <ul>
 *   <li>{@link #findById(UUID)}: tra cứu theo định danh.</li>
 *   <li>{@link #listByTree(UUID, boolean)}: liệt kê sự kiện của cây
 *       (có/không bao gồm tombstone).</li>
 *   <li>{@link #danglingReferences(DomainEvent)}: tập tham chiếu đứt
 *       trong payload — phục vụ endpoint reconciliation.</li>
 * </ul>
 *
 * @author gia-pha platform
 */
@Service
public class QueryDomainEventUseCase {

    private final EventRepository repo;
    private final ReferenceAvailability refs;

    /**
     * Khởi tạo use case.
     *
     * @param repo kho lưu trữ.
     * @param refs kiểm tra tham chiếu khả dụng (cho {@link #danglingReferences}).
     */
    public QueryDomainEventUseCase(EventRepository repo, ReferenceAvailability refs) {
        this.repo = repo;
        this.refs = refs;
    }

    /**
     * Tìm aggregate theo ID.
     *
     * @param id định danh duy nhất.
     * @return {@link Optional} chứa aggregate hoặc rỗng.
     */
    public Optional<DomainEvent> findById(UUID id) {
        return repo.findById(id);
    }

    /**
     * Liệt kê sự kiện của một cây.
     *
     * @param treeId             định danh cây.
     * @param includeTombstoned  {@code true} để bao gồm cả sự kiện đã xóa.
     * @return danh sách aggregate.
     */
    public List<DomainEvent> listByTree(UUID treeId, boolean includeTombstoned) {
        return repo.listByTree(treeId, includeTombstoned);
    }

    /**
     * Tính tập <b>tham chiếu đứt</b> trong payload của một sự kiện.
     *
     * <p>Quy trình:
     * <ol>
     *   <li>Tập hợp tất cả ID thành viên ({@code primary} +
     *       {@code additional}) vào một {@link HashSet}.</li>
     *   <li>Tra cứu {@link ReferenceAvailability#danglingMembers} để
     *       lấy phần chưa khả dụng.</li>
     * </ol>
     *
     * <p>Được dùng bởi endpoint <i>reconciliation</i> để bề mặt
     * projection lag mà không từ chối lệnh.
     *
     * @param ev aggregate cần kiểm tra.
     * @return tập (con của các ID tham chiếu) không còn khả dụng.
     */
    public Set<UUID> danglingReferences(DomainEvent ev) {
        // Tập hợp mọi ID tham chiếu trong payload của sự kiện.
        Set<UUID> all = new HashSet<>();
        if (ev.primaryMemberId() != null) all.add(ev.primaryMemberId());
        all.addAll(ev.additionalMemberIds());
        // Ủy quyền cho projection kiểm tra tính khả dụng.
        return refs.danglingMembers(ev.treeId(), all);
    }
}
