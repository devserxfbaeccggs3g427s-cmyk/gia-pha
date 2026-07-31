package com.familya.media.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Port ra (driven port) của kiến trúc hexagonal: lưu trữ tham chiếu từ
 * media tới đối tượng nghiệp vụ (MEMBER/EVENT/ALBUM) và cung cấp các
 * phương thức hỗ trợ compensation cho delete-member Saga.
 *
 * <p>Hợp đồng:</p>
 * <ul>
 *   <li>Một (mediaId, targetKind, targetId) là duy nhất; {@link #upsert}
 *       chèn hoặc cập nhật.</li>
 *   <li>Status {@code PENDING} / {@code ACTIVE} cho biết tham chiếu có
 *       đang trỏ tới target hợp lệ hay chưa.</li>
 *   <li>Các phương thức default đảm bảo interface luôn binary-compatible:
 *       triển khai cơ bản dùng {@link #clear} tuần tự; triển khai JDBC
 *       có thể override để chạy bulk SQL nguyên tử.</li>
 * </ul>
 */
public interface MediaReferenceRepository {

    /**
     * Chèn hoặc cập nhật một tham chiếu media.
     *
     * @param mediaId    UUID media.
     * @param treeId     UUID family-tree.
     * @param targetKind loại target, vd. {@code MEMBER}.
     * @param targetId   UUID target.
     * @param status     trạng thái, vd. {@code PENDING}, {@code ACTIVE}.
     * @param at         mốc thời gian cập nhật.
     * @param errorCode  mã lỗi nếu tham chiếu không khả dụng; null khi
     *                   thành công.
     */
    void upsert(UUID mediaId, UUID treeId, String targetKind, UUID targetId, String status, Instant at, String errorCode);

    /**
     * Xóa một tham chiếu media cụ thể.
     *
     * @param mediaId    UUID media.
     * @param targetKind loại target.
     * @param targetId   UUID target.
     */
    void clear(UUID mediaId, String targetKind, UUID targetId);

    /**
     * Liệt kê tất cả tham chiếu của một media.
     *
     * @param mediaId UUID media.
     * @return danh sách tham chiếu; rỗng nếu media chưa gắn với target
     *         nào.
     */
    List<ReferenceRow> listForMedia(UUID mediaId);

    /**
     * Liệt kê tham chiếu theo target.
     *
     * @param treeId     UUID family-tree (lọc phạm vi).
     * @param targetKind loại target; nếu truyền targetKind nhưng
     *                   {@code targetId == null} sẽ trả về mọi target
     *                   thuộc loại đó trong tree (dùng cho bulk clear).
     * @param targetId   UUID target; null nghĩa là "mọi target".
     * @return danh sách tham chiếu khớp điều kiện.
     */
    List<ReferenceRow> listByTarget(UUID treeId, String targetKind, UUID targetId);

    /**
     * Persist compensation snapshot for the delete-member Saga. Default no-op.
     *
     * @param operationId  UUID định danh Saga.
     * @param snapshotJson payload JSON đã serialize; bên triển khai có
     *                     thể lưu DB / cache / blob tùy chiến lược.
     */
    default void saveCompensationSnapshot(UUID operationId, String snapshotJson) { /* no-op */ }

    /**
     * Restore MEMBER-kind references that were cleared by the detach step.
     * Default returns 0 so the interface stays binary-compatible.
     *
     * @param operationId UUID Saga đã lưu snapshot.
     * @return số tham chiếu được khôi phục.
     */
    default int restoreMemberReferences(UUID operationId) { return 0; }

    /**
     * Bulk clear references whose target belongs to the tree.
     *
     * <p>Mặc định duyệt {@link #listByTarget(UUID, String, UUID)} theo
     * từng kind rồi gọi {@link #clear(UUID, String, UUID)} tuần tự; triển
     * khai JDBC có thể override bằng câu lệnh bulk nguyên tử.</p>
     *
     * @param treeId     UUID family-tree.
     * @param targetKinds các loại target cần clear.
     * @return số tham chiếu đã xóa.
     */
    default int bulkClearByTree(UUID treeId, java.util.List<String> targetKinds) {
        int n = 0;
        for (String kind : targetKinds) {
            for (ReferenceRow row : listByTarget(treeId, kind, null)) {
                clear(row.mediaId(), kind, row.targetId());
                n++;
            }
        }
        return n;
    }

    /**
     * Một dòng tham chiếu media.
     *
     * @param mediaId       UUID media.
     * @param treeId        UUID family-tree.
     * @param targetKind    loại target.
     * @param targetId      UUID target.
     * @param status        trạng thái.
     * @param lastAttemptAt mốc thời gian thử cuối.
     * @param lastErrorCode mã lỗi cuối (null nếu thành công).
     */
    record ReferenceRow(UUID mediaId, UUID treeId, String targetKind, UUID targetId, String status, Instant lastAttemptAt, String lastErrorCode) { }
}