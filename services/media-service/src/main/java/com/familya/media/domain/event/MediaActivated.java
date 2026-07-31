package com.familya.media.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Sự kiện phát ra khi một tài sản media được kích hoạt (chính thức công khai).
 *
 * <p>Sự kiện này đánh dấu thời điểm một {@link com.familya.media.domain.model.MediaAsset}
 * hoàn tất quá trình cách ly/quét và chuyển sang trạng thái sẵn sàng phục vụ.
 * Các hệ thống consumer sẽ dựa vào sự kiện này để tạo thumbnail, cập nhật
 * chỉ mục tìm kiếm hoặc thông báo cho người dùng liên quan.
 *
 * @param treeId      định danh cây gia phả chứa tài sản
 * @param mediaId     định danh của tài sản media được kích hoạt
 * @param revision    số phiên bản của tài sản tại thời điểm phát sự kiện
 * @param occurredAt  thời điểm sự kiện được phát ra
 * @param activatedAt thời điểm tài sản thực sự được kích hoạt trong hệ thống
 */
public record MediaActivated(
        UUID treeId,
        UUID mediaId,
        long revision,
        Instant occurredAt,
        Instant activatedAt
) implements MediaChange {
    @Override public String eventType() { return "MediaActivated"; }
    @Override public int eventVersion() { return 1; }
    @Override public String topic() { return "media.events.v1"; }
    @Override public String partitionKey() { return treeId.toString(); }
}
