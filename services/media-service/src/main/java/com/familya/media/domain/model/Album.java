package com.familya.media.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Album trong cây gia phả.
 *
 * <p>Đại diện cho một tập hợp các tài sản media (ảnh, video, audio, tài liệu) được nhóm lại
 * theo chủ đề, sự kiện hoặc giai đoạn trong cây gia phả. Album thuộc về một cây gia phả
 * ({@code treeId}) và có thể có một ảnh bìa tham chiếu đến một {@link MediaAsset}.
 *
 * <p>Là bất biến (immutable) - mọi thay đổi đều tạo ra bản ghi mới với {@code version}
 * tăng dần để phục vụ cho cơ chế optimistic locking. Trường {@code tombstonedAt}
 * đánh dấu album đã bị xóa mềm nhưng vẫn cần giữ lại cho mục đích truy vết và khôi phục.
 *
 * @param id            định danh duy nhất của album
 * @param treeId        định danh cây gia phả mà album thuộc về
 * @param name          tên hiển thị của album
 * @param description   mô tả chi tiết về album, có thể null
 * @param coverMediaId  định danh của {@link MediaAsset} được dùng làm ảnh bìa, có thể null
 * @param createdAt     thời điểm album được tạo
 * @param updatedAt     thời điểm album được cập nhật lần cuối
 * @param version       phiên bản của bản ghi, dùng cho optimistic locking
 * @param tombstonedAt  thời điểm album bị xóa mềm, null nếu còn hoạt động
 */
public record Album(
        UUID id,
        UUID treeId,
        String name,
        String description,
        UUID coverMediaId,
        Instant createdAt,
        Instant updatedAt,
        long version,
        Instant tombstonedAt
) { }
