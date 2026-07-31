package com.familya.platform.projection;

import java.time.Instant;
import java.util.UUID;

/**
 * Header chung cho mọi projection (mô hình đọc).
 *
 * <p>Mỗi mô hình đọc do dịch vụ sở hữu cần mang các thông tin tối thiểu sau:</p>
 * <ul>
 *   <li><b>aggregateId</b> — định danh aggregate gốc (ví dụ: treeId, memberId, ...).</li>
 *   <li><b>revision</b> — revision/epoch của projection.</li>
 *   <li><b>epoch</b> — revision của aggregate nguồn có thẩm quyền mà projection
 *       đang phản ánh (projection luôn biết aggregate nguồn ở phiên bản nào).</li>
 *   <li><b>lastUpdatedAt</b> — thời điểm cập nhật gần nhất, dùng cho kiểm tra độ tươi.</li>
 *   <li><b>watermark</b> — watermark theo từng topic nguồn, phục vụ replay và phát hiện khoảng trống.</li>
 *   <li><b>originEventId</b> — id của sự kiện gốc, dùng để liên kết với cơ chế dedupe inbox.</li>
 * </ul>
 *
 * <p>Đây là metadata tối thiểu mà dịch vụ cần để đưa ra quyết định uỷ quyền
 * an toàn hoặc đối chiếu với dịch vụ nguồn. Cấu trúc này được thiết kế độc
 * lập với framework để SDK projection có thể tái sử dụng xuyên suốt các
 * dịch vụ Member, Relationship, Event, Media, Sharing, Search, Transfer.</p>
 *
 * @author Family Tree Platform Team
 */
public record ProjectionHeader(
        UUID aggregateId,
        long revision,
        long epoch,
        Instant lastUpdatedAt,
        long watermark,
        String originEventId
) {
    /**
     * Tạo bản sao của header với revision mới. Hữu ích cho các trường hợp
     * cần cập nhật revision trong khi giữ nguyên các trường khác.
     *
     * @param newRevision revision mới
     * @return {@link ProjectionHeader} với revision được cập nhật
     */
    public ProjectionHeader withRevision(long newRevision) {
        // Tạo bản sao mới với revision mới, giữ nguyên các trường khác.
        return new ProjectionHeader(aggregateId, newRevision, epoch, lastUpdatedAt, watermark, originEventId);
    }
}
