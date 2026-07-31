package com.familya.media.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Sự kiện phát ra khi một tài sản media được tách khỏi thực thể liên quan.
 *
 * <p>Là sự kiện ngược với {@link MediaAssociated}, sự kiện này thông báo rằng
 * mối liên kết giữa tài sản media và một thực thể (thành viên, sự kiện, album, ...)
 * đã bị xóa. Consumer sử dụng sự kiện này để cập nhật chỉ mục, làm sạch dữ liệu
 * tham chiếu hoặc gỡ bỏ hiển thị liên quan.
 *
 * @param treeId       định danh cây gia phả chứa tài sản
 * @param mediaId      định danh của tài sản media bị tách liên kết
 * @param revision     số phiên bản của tài sản tại thời điểm phát sự kiện
 * @param occurredAt   thời điểm sự kiện được phát ra
 * @param relationKind loại quan hệ bị tách (tên loại quan hệ, ví dụ: "avatar", "cover")
 */
public record MediaDetached(
        UUID treeId,
        UUID mediaId,
        long revision,
        Instant occurredAt,
        String relationKind
) implements MediaChange {
    @Override public String eventType() { return "MediaDetached"; }
    @Override public int eventVersion() { return 1; }
    @Override public String topic() { return "media.events.v1"; }
    @Override public String partitionKey() { return treeId.toString(); }
}
