package com.familya.media.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Sự kiện phát ra khi một tài sản media được gắn kết với một thực thể đích.
 *
 * <p>Thực thể đích có thể là thành viên trong gia phả, sự kiện, địa điểm, album
 * hoặc bất kỳ đối tượng nghiệp vụ nào khác. Sự kiện này cho phép các hệ thống
 * downstream đồng bộ mối liên kết giữa media và thực thể đích mà không cần
 * truy vấn trực tiếp vào cơ sở dữ liệu.
 *
 * @param treeId      định danh cây gia phả chứa tài sản
 * @param mediaId     định danh của tài sản media được gắn kết
 * @param revision    số phiên bản của tài sản tại thời điểm phát sự kiện
 * @param occurredAt  thời điểm sự kiện được phát ra
 * @param targetKind  loại thực thể đích (ví dụ: "Person", "Event", "Place")
 * @param targetId    định danh của thực thể đích
 */
public record MediaAssociated(
        UUID treeId,
        UUID mediaId,
        long revision,
        Instant occurredAt,
        String targetKind,
        UUID targetId
) implements MediaChange {
    @Override public String eventType() { return "MediaAssociated"; }
    @Override public int eventVersion() { return 1; }
    @Override public String topic() { return "media.events.v1"; }
    @Override public String partitionKey() { return treeId.toString(); }
}
