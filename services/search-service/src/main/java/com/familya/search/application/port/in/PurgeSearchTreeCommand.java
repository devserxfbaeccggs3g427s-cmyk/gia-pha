package com.familya.search.application.port.in;

import java.util.UUID;

/**
 * Lệnh (command) yêu cầu xoá tất cả tài liệu tìm kiếm của một cây gia phả.
 *
 * <p>Đây là một bước tham gia (participant step) trong Saga xoá cây: khi Saga
 * điều phối việc xoá một cây, mỗi service tham gia nhận lệnh riêng để xoá
 * phần dữ liệu của mình. Service search nhận lệnh này và xoá mọi bản ghi
 * trong các bảng {@code search_*} cùng các bảng phụ trợ.</p>
 *
 * @param operationId          định danh thao tác Saga (dùng để log và truy
 *                             vết đối chiếu).
 * @param treeId               định danh cây gia phả cần xoá.
 * @param targetAggregateVersion phiên bản aggregate mà watermark sẽ được
 *                              nâng lên sau khi xoá xong.
 * @param targetEpoch          epoch tương ứng của aggregate, ghi nhận "thời
 *                              đại" mà việc xoá đã diễn ra.
 */
public record PurgeSearchTreeCommand(
        UUID operationId,
        UUID treeId,
        long targetAggregateVersion,
        long targetEpoch) {
}