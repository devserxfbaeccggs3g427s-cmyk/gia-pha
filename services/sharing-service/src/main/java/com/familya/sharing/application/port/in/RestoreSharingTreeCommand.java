package com.familya.sharing.application.port.in;

import java.util.UUID;

/**
 * Lệnh đầu vào yêu cầu khôi phục (restore) các liên kết chia sẻ trên một cây
 * gia phả &mdash; thường được sử dụng như bước bù (compensation) trong saga
 * xóa cây.
 * <p>
 * Lệnh này được gửi từ {@code DeleteTreeSagaCommandListener} khi saga nhận
 * được yêu cầu bù trừ {@code RESTORE_SHARING_TREE}. Use case sẽ quét tất cả
 * các liên kết của cây, lọc ra những liên kết đã bị thu hồi bởi saga xóa cây
 * (được đánh dấu bằng {@code revocationReason} bắt đầu bằng
 * {@code "delete-tree-saga:"}) và khôi phục chúng về trạng thái hoạt động.
 *
 * @param operationId định danh của thao tác saga (dùng cho truy vết/log).
 * @param treeId      định danh cây gia phả cần khôi phục các liên kết.
 */
public record RestoreSharingTreeCommand(
        UUID operationId,
        UUID treeId) {
}