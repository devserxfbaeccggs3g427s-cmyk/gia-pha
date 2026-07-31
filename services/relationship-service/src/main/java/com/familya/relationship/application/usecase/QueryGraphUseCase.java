package com.familya.relationship.application.usecase;

import com.familya.relationship.application.port.out.RelationshipRepository;
import com.familya.relationship.domain.graph.GraphAlgorithms;
import com.familya.relationship.domain.model.Relationship;

import java.util.*;

/**
 * Use case chỉ-đọc (read-only) cho phép truy vấn các thuật toán trên đồ thị gia phả.
 * <p>
 * Lớp này hoạt động như một "facade" mỏng gọn, ủy quyền cho
 * {@link GraphAlgorithms} sau khi nạp tập quan hệ phù hợp từ repository. Được
 * sử dụng bởi:
 * </p>
 * <ul>
 *   <li>Các REST/GraphQL adapter phục vụ UI dạng cây gia phả.</li>
 *   <li>Endpoint migration/reconciliation để so sánh trạng thái với hệ thống cũ.</li>
 *   <li>Các công cụ nội bộ phục vụ debug hoặc audit.</li>
 * </ul>
 *
 * <p>
 * Các phương thức đều không có side-effect và không thay đổi DB; chúng có thể
 * được gọi đồng thời từ nhiều request mà không cần khóa.
 * </p>
 */
@org.springframework.stereotype.Service
public class QueryGraphUseCase {

    /** Repository chỉ dùng cho mục đích đọc. */
    private final RelationshipRepository repo;

    /**
     * Khởi tạo use case.
     *
     * @param repo repository quan hệ
     */
    public QueryGraphUseCase(RelationshipRepository repo) {
        this.repo = repo;
    }

    /**
     * Tính thế hệ cho mọi thành viên trong cây bắt đầu từ {@code root}.
     *
     * @param treeId định danh cây gia phả
     * @param root   thành viên gốc (thế hệ 0)
     * @return {@code Map} từ định danh thành viên sang số thế hệ
     */
    public Map<UUID, Integer> generations(UUID treeId, UUID root) {
        // Truyền includeTombstoned=false: chỉ quan hệ đang sống mới ảnh hưởng đến thế hệ.
        return GraphAlgorithms.generations(repo.listByTree(treeId, false), root);
    }

    /**
     * Trả về tập tổ tiên (transitive) của một thành viên.
     * <p>
     * Khác với {@link #generations(UUID, UUID)}, API này truyền
     * {@code includeTombstoned=true} để có thể truy vấn cả các quan hệ đã xóa
     * (phục vụ mục đích audit hoặc hiển thị lịch sử).
     * </p>
     *
     * @param treeId định danh cây
     * @param member thành viên cần truy vấn tổ tiên
     * @return tập định danh các tổ tiên
     */
    public Set<UUID> ancestors(UUID treeId, UUID member) {
        return GraphAlgorithms.ancestors(repo.listByTree(treeId, true), member);
    }

    /**
     * Trả về tập vợ/chồng của một thành viên (chỉ tính các quan hệ đang sống).
     *
     * @param treeId định danh cây
     * @param member thành viên cần truy vấn
     * @return tập định danh vợ/chồng
     */
    public Set<UUID> spouses(UUID treeId, UUID member) {
        return GraphAlgorithms.spouses(repo.listByTree(treeId, false), member);
    }

    /**
     * Trả về tập các cặp nhận nuôi liên quan tới một thành viên (chỉ tính quan hệ đang sống).
     *
     * @param treeId định danh cây
     * @param member thành viên cần truy vấn
     * @return tập định danh liên quan
     */
    public Set<UUID> adoptions(UUID treeId, UUID member) {
        return GraphAlgorithms.adoptions(repo.listByTree(treeId, false), member);
    }

    /**
     * Liệt kê toàn bộ quan hệ trong cây (kèm tùy chọn bao gồm đã tombstone).
     *
     * @param treeId             định danh cây
     * @param includeTombstoned  {@code true} nếu muốn bao gồm quan hệ đã xóa mềm
     * @return danh sách quan hệ
     */
    public List<Relationship> list(UUID treeId, boolean includeTombstoned) {
        return repo.listByTree(treeId, includeTombstoned);
    }
}