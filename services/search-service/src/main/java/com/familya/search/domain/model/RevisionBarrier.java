package com.familya.search.domain.model;

import java.util.Map;
import java.util.UUID;

/**
 * "Rào chắn" (barrier) phiên bản dùng để kiểm tra tính hội tụ của các miền
 * dữ liệu (member/event/media/tree/relationship) cho một cây gia phả.
 *
 * <p>Một {@code RevisionBarrier} lưu trữ giá trị watermark hiện tại theo từng
 * {@link Watermark.Domain}. Khi tất cả các miền đều đạt đến mức mong đợi thì
 * mới an toàn để đọc - tránh tình trạng một số miền đã cập nhật mà miền khác
 * chưa kịp (read-your-writes xuyên miền).</p>
 *
 * @param treeId  định danh cây gia phả.
 * @param values  bản đồ từ miền sang giá trị watermark tương ứng.
 */
public record RevisionBarrier(
        UUID treeId,
        Map<Watermark.Domain, Long> values
) { }
