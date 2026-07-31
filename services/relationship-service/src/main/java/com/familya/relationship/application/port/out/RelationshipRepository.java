package com.familya.relationship.application.port.out;

import com.familya.relationship.domain.model.Relationship;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cổng (port) ra phía persistence cho aggregate {@link Relationship}.
 * <p>
 * Đây là một <b>interface</b> thuộc tầng application; implementation cụ thể
 * (JDBC, JPA, jOOQ, ...) nằm ở tầng adapter. Việc tách rời này đảm bảo logic
 * nghiệp vụ không phụ thuộc vào công nghệ lưu trữ, đồng thời dễ dàng thay
 * thế hoặc test với các implementation giả (in-memory).
 * </p>
 *
 * <h2>Các khái niệm quan trọng</h2>
 * <ul>
 *   <li><b>Command log:</b> bảng {@code graph_command_log} ghi lại mọi lệnh
 *       ghi theo thứ tự (per-tree monotonic sequence). Đây là cơ chế cốt lõi
 *       cho việc tái dựng và đối chiếu projection.</li>
 *   <li><b>Optimistic concurrency:</b> các thao tác ghi cần truyền phiên bản
 *       mong đợi; repository sẽ từ chối cập nhật nếu phiên bản không khớp.</li>
 *   <li><b>Compensation snapshot:</b> bảng {@code saga_compensation_snapshot}
 *       lưu trạng thái trước khi saga thay đổi dữ liệu, cho phép undo.</li>
 * </ul>
 */
public interface RelationshipRepository {

    /**
     * Chèn một quan hệ mới vào cơ sở dữ liệu.
     * <p>
     * Phương thức này phải được gọi trong một transaction đang mở; thường là
     * từ use case có annotation {@code @Transactional}.
     * </p>
     *
     * @param rel aggregate quan hệ cần chèn, không null
     * @throws org.springframework.dao.DuplicateKeyException nếu bộ bốn
     *         {@code (treeId, kind, fromMember, toMember)} đã tồn tại
     */
    void insert(Relationship rel);

    /**
     * Tra cứu một quan hệ theo {@code id}.
     *
     * @param id định danh quan hệ
     * @return {@code Optional} chứa quan hệ nếu tìm thấy, {@code Optional.empty()} nếu không
     */
    Optional<Relationship> findById(UUID id);

    /**
     * Liệt kê tất cả quan hệ trong một cây.
     *
     * @param treeId             định danh cây gia phả
     * @param includeTombstoned  {@code true} nếu muốn bao gồm cả quan hệ đã xóa mềm
     * @return danh sách quan hệ (có thể rỗng nhưng không null)
     */
    List<Relationship> listByTree(UUID treeId, boolean includeTombstoned);

    /**
     * Liệt kê các quan hệ <b>đang hoạt động</b> (chưa tombstone) mà liên quan
     * tới một thành viên theo bất kỳ chiều nào (fromMember hoặc toMember).
     * <p>
     * Đây là API quan trọng cho saga xóa thành viên.
     * </p>
     *
     * @param treeId   định danh cây gia phả
     * @param memberId định danh thành viên cần truy vấn
     * @return danh sách các quan hệ đang hoạt động liên quan tới {@code memberId}
     */
    List<Relationship> listActiveByMember(UUID treeId, UUID memberId);

    /**
     * Trả về số thứ tự lệnh tiếp theo cho cây (dùng cho bộ tuần tự hóa theo cây).
     * <p>
     * Thao tác này phải được thực hiện bên trong một transaction với
     * {@code SELECT … FOR UPDATE} để đảm bảo các transaction đồng thời trên
     * cùng cây được xếp hàng nối tiếp; các cây khác nhau vẫn xử lý song song.
     * </p>
     *
     * @param treeId định danh cây gia phả
     * @return số thứ tự lệnh tiếp theo (bắt đầu từ 1)
     */
    long nextCommandSeq(UUID treeId);

    /**
     * Ghi thêm một dòng vào command log. Dòng này phản ánh lệnh vừa được áp
     * dụng thành công lên aggregate và là nguồn sự thật cho việc tái dựng.
     *
     * @param treeId      định danh cây gia phả
     * @param commandSeq  số thứ tự lệnh (do {@link #nextCommandSeq} trả về)
     * @param commandType loại lệnh (ví dụ: {@code "create_relationship"},
     *                    {@code "tombstone_relationship"})
     * @param actorUserId định danh người dùng thực hiện lệnh
     * @param payloadHash mã băm tóm tắt payload (dùng cho kiểm tra toàn vẹn)
     * @param committedAt thời điểm ghi nhận lệnh
     */
    void appendCommandLog(UUID treeId, long commandSeq, String commandType,
                          UUID actorUserId, String payloadHash, java.time.Instant committedAt);

    /**
     * Cập nhật một quan hệ đã tồn tại (thường là để đánh tombstone).
     *
     * @param rel aggregate chứa trạng thái mới (đã được aggregate xử lý)
     */
    void update(Relationship rel);

    /**
     * Bỏ tombstone một quan hệ (dùng trong bước khôi phục của saga).
     *
     * @param id              định danh quan hệ
     * @param at              thời điểm khôi phục (ghi nhận trong trường version)
     * @param expectedVersion phiên bản hiện tại (để kiểm tra xung đột)
     */
    void untombstone(UUID id, Instant at, long expectedVersion);

    /**
     * Kiểm tra xem một cạnh {@code (tree, kind, from, to)} đã tồn tại chưa.
     *
     * @param treeId định danh cây gia phả
     * @param kind   loại quan hệ
     * @param from   thành viên phía nguồn
     * @param to     thành viên phía đích
     * @return {@code true} nếu cạnh đã tồn tại (đang sống hoặc đã tombstone)
     */
    boolean existsEdge(UUID treeId, Relationship.Kind kind, UUID from, UUID to);

    /**
     * Lưu snapshot bồi thường cho một thao tác saga (best-effort, idempotent).
     *
     * @param operationId  định danh thao tác saga
     * @param snapshotJson chuỗi JSON mô tả trạng thái cần khôi phục
     */
    void saveCompensationSnapshot(UUID operationId, String snapshotJson);

    /**
     * Tải snapshot bồi thường đã lưu trước đó.
     *
     * @param operationId định danh thao tác saga
     * @return chuỗi JSON của snapshot hoặc {@code null} nếu không tồn tại
     */
    String loadCompensationSnapshot(UUID operationId);

    /**
     * Tombstone hàng loạt mọi quan hệ chưa tombstone trong một cây.
     * <p>
     * Đây là phương thức mặc định (default method) - triển khai mặc định duyệt
     * tuần tự qua từng quan hệ; implementation cụ thể có thể ghi đè để tối ưu
     * bằng một câu lệnh SQL duy nhất.
     * </p>
     *
     * @param treeId định danh cây gia phả
     * @param at    thời điểm áp dụng tombstone cho mọi quan hệ
     * @return số quan hệ đã được đánh tombstone
     */
    default int bulkTombstoneByTree(UUID treeId, java.time.Instant at) {
        int n = 0;
        // Duyệt từng quan hệ chưa tombstone, gọi tombstone() trên aggregate
        // rồi cập nhật DB. Việc xử lý trên aggregate đảm bảo version tăng đúng.
        for (Relationship r : listByTree(treeId, false)) {
            r.tombstone(r.version(), at);
            update(r);
            n++;
        }
        return n;
    }
}