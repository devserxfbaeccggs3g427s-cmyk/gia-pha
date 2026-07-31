package com.familya.media.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Port ra (driven port) của kiến trúc hexagonal: quản lý retention hold
 * trên media.
 *
 * <p>Retention hold đảm bảo binary chỉ bị cleanup worker xóa sau khi
 * deadline {@code holdUntil} qua. Cơ chế này bảo vệ khỏi:</p>
 * <ul>
 *   <li>Xóa nhầm ngay khi user request delete (grace period).</li>
 *   <li>Race giữa Saga compensation và cleanup worker.</li>
 * </ul>
 */
public interface MediaRetentionRepository {

    /**
     * Đặt một retention hold lên media.
     *
     * @param mediaId  UUID media cần giữ binary.
     * @param treeId   UUID family-tree.
     * @param holdUntil deadline mà cleanup worker được phép xóa.
     * @param reason   lý do đặt hold (audit); vd. {@code user-requested},
     *                 {@code delete-tree-saga:{operationId}}.
     */
    void placeHold(UUID mediaId, UUID treeId, Instant holdUntil, String reason);

    /**
     * Giải phóng một retention hold theo id.
     *
     * @param holdId     UUID của hold.
     * @param releasedAt mốc thời gian giải phóng.
     */
    void release(UUID holdId, Instant releasedAt);

    /**
     * Liệt kê các hold đã đến hạn (cleanup-eligible).
     *
     * @param now   mốc thời gian hiện tại.
     * @param limit số row tối đa trả về.
     * @return danh sách các hold có {@code holdUntil <= now}.
     */
    List<HoldRow> listReadyForCleanup(Instant now, int limit);

    /**
     * Release any hold placed by {@code operationId}. Default no-op.
     *
     * <p>Dùng trong bước compensation của delete-tree Saga: khi rollback,
     * cần giải phóng các hold đã đặt theo {@code operationId} để binary
     * không bị xóa.</p>
     *
     * @param mediaId     UUID media.
     * @param operationId UUID Saga.
     * @return số hold đã giải phóng.
     */
    default int releaseHoldForOperation(UUID mediaId, UUID operationId) { return 0; }

    /**
     * Bulk place retention holds on every media row in the tree.
     *
     * @param treeId    UUID family-tree.
     * @param holdUntil deadline chung.
     * @param reason    lý do đặt hold.
     * @return số hold đã đặt.
     */
    default int bulkPlaceHoldByTree(UUID treeId, Instant holdUntil, String reason) { return 0; }

    /**
     * Một dòng retention hold.
     *
     * @param id        UUID hold.
     * @param mediaId   UUID media.
     * @param treeId    UUID family-tree.
     * @param holdUntil deadline.
     * @param reason    lý do (audit).
     */
    record HoldRow(UUID id, UUID mediaId, UUID treeId, Instant holdUntil, String reason) { }
}
