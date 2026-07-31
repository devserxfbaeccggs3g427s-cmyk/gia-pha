package com.familya.media.application.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Per-tree per-domain watermark. Each mutation advances the
 * watermark atomically; reconciliation replays deltas by reading
 * rows whose {@code updated_at} sits after the stored watermark.
 *
 * <p>Port ra (driven port) của kiến trúc hexagonal: lưu trữ "mốc nước"
 * (watermark) cho mỗi cặp (tree, domain). Reconciliation worker dùng
 * watermark để replay delta sang downstream (search index, projection
 * khác).</p>
 */
public interface MediaWatermarkRepository {

    /**
     * Đọc watermark hiện tại của một domain trên một tree.
     *
     * @param treeId UUID family-tree.
     * @param domain tên domain, vd. {@code media-asset},
     *               {@code media-reference}.
     * @return mảng {@code long[]} mô tả watermark (thường là
     *         {@code [epochMs, sequence]}); rỗng nếu chưa từng có.
     */
    Optional<long[]> read(UUID treeId, String domain);

    /**
     * Nâng watermark lên giá trị mới, atomic.
     *
     * <p>Triển khai phải đảm bảo nếu có hai caller advance đồng thời
     * thì giá trị cuối cùng bằng max của các giá trị được cung cấp
     * (không được giảm).</p>
     *
     * @param treeId       UUID family-tree.
     * @param domain       tên domain.
     * @param newWatermark giá trị watermark mới.
     * @param now          mốc thời gian cập nhật.
     * @return giá trị watermark thực sự được lưu sau lần advance này.
     */
    long advance(UUID treeId, String domain, long newWatermark, java.time.Instant now);

    /**
     * Bản ghi watermark.
     *
     * @param treeId      UUID family-tree.
     * @param domain      tên domain.
     * @param watermark   giá trị watermark.
     * @param lastUpdated mốc thời gian cập nhật.
     */
    record Watermark(UUID treeId, String domain, long watermark, java.time.Instant lastUpdated) { }
}
