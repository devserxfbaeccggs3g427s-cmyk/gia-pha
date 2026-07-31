package com.familya.media.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Sự kiện phát ra khi một tài sản media hoàn tất quá trình quét an toàn.
 *
 * <p>Sự kiện này được phát ra sau khi bộ quét (scanner) xử lý xong tài sản media
 * ở trạng thái {@link com.familya.media.domain.model.MediaAsset.Status#SCANNING}.
 * Trường {@code verdict} chứa phán đoán cuối cùng (ví dụ: "CLEAN", "INFECTED",
 * "FAILED") và sẽ là đầu vào để hệ thống quyết định chuyển sang trạng thái
 * {@code READY} hay đưa vào cách ly.
 *
 * @param treeId     định danh cây gia phả chứa tài sản
 * @param mediaId    định danh của tài sản media vừa được quét
 * @param revision   số phiên bản của tài sản tại thời điểm phát sự kiện
 * @param occurredAt thời điểm sự kiện được phát ra
 * @param verdict    phán đoán an toàn dạng chuỗi (tương ứng với
 *                   {@link com.familya.media.domain.model.ScannerResult.Outcome})
 */
public record MediaScanned(
        UUID treeId,
        UUID mediaId,
        long revision,
        Instant occurredAt,
        String verdict
) implements MediaChange {
    @Override public String eventType() { return "MediaScanned"; }
    @Override public int eventVersion() { return 1; }
    @Override public String topic() { return "media.events.v1"; }
    @Override public String partitionKey() { return treeId.toString(); }
}
