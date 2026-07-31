package com.familya.sharing.application.port.in;

import java.util.UUID;

/**
 * Lệnh đầu vào yêu cầu thu hồi (revoke) tất cả các liên kết chia sẻ thuộc về
 * một cây gia phả &mdash; thường được sử dụng như một bước trong saga xóa cây.
 * <p>
 * Lệnh được phát ra bởi {@code DeleteTreeSagaCommandListener} khi saga nhận
 * được yêu cầu {@code REVOKE_SHARING_TREE}. Khi thực thi, use case sẽ lặp qua
 * tất cả các liên kết của cây, đánh dấu chúng là đã thu hồi với lý do
 * {@code "delete-tree-saga:<operationId>"} để sau này có thể khôi phục được.
 *
 * @param operationId           định danh của thao tác saga (dùng cho truy vết/log
 *                              và đánh dấu lý do thu hồi).
 * @param treeId                định danh cây gia phả cần thu hồi toàn bộ liên kết.
 * @param targetAggregateVersion phiên bản kỳ vọng mà saga yêu cầu đạt được.
 * @param targetEpoch           epoch kỳ vọng của bước trong saga.
 */
public record RevokeSharingTreeCommand(
        UUID operationId,
        UUID treeId,
        long targetAggregateVersion,
        long targetEpoch) {
}