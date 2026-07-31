package com.familya.treeaccess.application.port.in;

import java.util.UUID;

/**
 * Lệnh tăng revision/epoch do orchestrator phát ra. Chỉ ADMIN mới có quyền
 * và cây phải đang ACTIVE.
 *
 * @param treeId          mã cây
 * @param actingUser      UUID người thực hiện
 * @param expectedVersion phiên bản kỳ vọng (cho optimistic locking)
 * @param newRevision     revision mục tiêu
 * @param newEpoch        epoch mục tiêu
 * @param reason          lý do tăng revision, dùng cho truy vết
 */
public record AdvanceRevisionCommand(UUID treeId, UUID actingUser, long expectedVersion, long newRevision, long newEpoch, String reason) { }