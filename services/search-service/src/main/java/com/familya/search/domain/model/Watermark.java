package com.familya.search.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Watermark (mốc nước) theo dõi mức tiến trình xử lý sự kiện của một miền
 * dữ liệu trong một cây gia phả cụ thể.
 *
 * <p>Mỗi miền ({@link Domain}) có một dãy watermark riêng. Khi consumer xử
 * lý xong một sự kiện, watermark được tăng lên bằng {@code GREATEST(value,
 * current)} - nghĩa là chỉ tiến lên, không bao giờ lùi. Nhờ vậy, khi cần
 * biết "tất cả sự kiện đến phiên bản {@code N} đã được xử lý hay chưa", ta
 * chỉ cần so sánh watermark hiện tại với {@code N}.</p>
 *
 * @param treeId      định danh cây gia phả.
 * @param domain      miền dữ liệu mà watermark này áp dụng.
 * @param value       giá trị watermark (thường là phiên bản aggregate).
 * @param lastUpdated thời điểm cập nhật watermark gần nhất.
 */
public record Watermark(
        UUID treeId,
        Domain domain,
        long value,
        Instant lastUpdated
) {
    /**
     * Các miền dữ liệu được theo dõi bằng watermark:
     * <ul>
     *   <li>{@code MEMBER} - thành viên trong cây</li>
     *   <li>{@code RELATIONSHIP} - quan hệ huyết thống/cha mẹ-con</li>
     *   <li>{@code EVENT} - sự kiện (sinh nhật, cưới, ...)</li>
     *   <li>{@code MEDIA} - tệp media (ảnh, video)</li>
     *   <li>{@code TREE} - thay đổi ở cấp cây (membership)</li>
     * </ul>
     */
    public enum Domain { MEMBER, RELATIONSHIP, EVENT, MEDIA, TREE }
}
