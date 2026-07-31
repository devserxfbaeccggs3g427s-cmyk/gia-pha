package com.familya.media.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Sự kiện phát ra khi một album mới được tạo trong cây gia phả.
 *
 * <p>Sự kiện này thuộc nhóm {@link MediaChange}, được gửi lên topic
 * {@code media.events.v1} với khóa phân vùng là {@code treeId} để đảm bảo
 * thứ tự xử lý trong cùng một cây gia phả. Consumer sử dụng sự kiện này
 * để đồng bộ chỉ mục tìm kiếm, thông báo cho thành viên hoặc cập nhật
 * bộ nhớ đệm.
 *
 * @param treeId     định danh cây gia phả nơi album được tạo
 * @param mediaId    định danh của album (đặt tên mediaId cho đồng nhất với các sự kiện khác)
 * @param revision   số phiên bản của album tại thời điểm phát sự kiện
 * @param occurredAt thời điểm sự kiện xảy ra
 * @param name       tên của album vừa được tạo
 */
public record AlbumCreated(
        UUID treeId,
        UUID mediaId,
        long revision,
        Instant occurredAt,
        String name
) implements MediaChange {
    @Override public String eventType() { return "AlbumCreated"; }
    @Override public int eventVersion() { return 1; }
    @Override public String topic() { return "media.events.v1"; }
    @Override public String partitionKey() { return treeId.toString(); }
}
