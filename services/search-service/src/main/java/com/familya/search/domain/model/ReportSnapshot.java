package com.familya.search.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Bản chụp nhanh (snapshot) của một báo cáo (report) đã tính toán xong cho cây.
 *
 * <p>Service này không sinh ra báo cáo thực sự - {@code payload} chỉ là một
 * chuỗi JSON mang thông tin đặc trưng cho {@code kind}. Báo cáo được lưu lại
 * với khoá là {@code (treeId, kind, watermark)} để có thể tái sử dụng khi các
 * lần truy vấn sau cùng mức watermark.</p>
 *
 * @param id          định danh duy nhất của bản chụp.
 * @param treeId      định danh cây gia phả.
 * @param kind        loại báo cáo (xem {@link Kind}).
 * @param payload     chuỗi JSON chứa dữ liệu báo cáo.
 * @param watermark   mức watermark mà báo cáo phản ánh; báo cáo chỉ hợp lệ với
 *                    watermark tại thời điểm tính.
 * @param computedAt  thời điểm sinh báo cáo.
 */
public record ReportSnapshot(
        UUID id,
        UUID treeId,
        Kind kind,
        String payload,
        long watermark,
        Instant computedAt
) {
    /**
     * Các loại báo cáo hiện được hỗ trợ.
     */
    public enum Kind { DEMOGRAPHICS, MEDIA_SUMMARY, EVENT_TIMELINE }
}
