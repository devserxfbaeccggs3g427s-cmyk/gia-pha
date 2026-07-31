package com.familya.media.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Sự kiện phát ra khi một tài sản media bị đưa vào vùng cách ly (quarantine).
 *
 * <p>Sự kiện này được phát ra khi tài sản media vi phạm chính sách an toàn (ví dụ:
 * nhiễm virus, vượt giới hạn kích thước, sai định dạng) hoặc khi cần tạm giữ
 * để chờ xử lý thêm. Hệ thống xử lý hậu kỳ sẽ dựa vào sự kiện này để thông báo
 * cho người dùng, đồng bộ trạng thái và bắt đầu quy trình xử lý phù hợp.
 *
 * @param treeId     định danh cây gia phả chứa tài sản
 * @param mediaId    định danh của tài sản media bị cách ly
 * @param revision   số phiên bản của tài sản tại thời điểm phát sự kiện
 * @param occurredAt thời điểm sự kiện được phát ra
 * @param phase      giai đoạn phát hiện vi phạm (ví dụ: "upload", "scan", "policy")
 * @param reason     mô tả ngắn gọn lý do cách ly, có thể dùng để hiển thị cho người dùng
 */
public record MediaQuarantined(
        UUID treeId,
        UUID mediaId,
        long revision,
        Instant occurredAt,
        String phase,
        String reason
) implements MediaChange {
    @Override public String eventType() { return "MediaQuarantined"; }
    @Override public int eventVersion() { return 1; }
    @Override public String topic() { return "media.events.v1"; }
    @Override public String partitionKey() { return treeId.toString(); }
}
