package com.familya.media.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Tài sản media trong hệ thống gia phả.
 *
 * <p>Đây là aggregate root chính của domain media, đại diện cho một tệp tin đã được
 * tải lên (ảnh, video, âm thanh, tài liệu hoặc loại khác). Mỗi {@code MediaAsset}
 * thuộc về một cây gia phả ({@code treeId}), có thể được gắn vào một album
 * ({@code albumId}) và gắn với một người sở hữu ({@code ownerUserId}).
 *
 * <p>Vòng đời của tài sản media được mô tả qua enum {@link Status}, trải qua các
 * trạng thái: cách ly khi mới tải lên, quét virus, sẵn sàng sử dụng, thất bại
 * hoặc đã bị xóa mềm. Trường {@code sha256} dùng để chống trùng lặp nội dung,
 * {@code retentionHoldUntil} để tạm giữ tài sản trước khi xóa vĩnh viễn, và
 * {@code tombstonedAt} để đánh dấu xóa mềm phục vụ truy vết.
 *
 * <p>Đối tượng này là bất biến - mọi thay đổi trạng thái đều tạo ra bản ghi mới
 * với {@code version} tăng dần (optimistic locking).
 *
 * @param id                  định danh duy nhất của tài sản media
 * @param treeId              định danh cây gia phả mà tài sản thuộc về
 * @param albumId             định danh album chứa tài sản, null nếu chưa gắn vào album
 * @param ownerUserId         định danh người dùng sở hữu tài sản
 * @param kind                loại tài sản (xem {@link Kind})
 * @param mimeType            kiểu MIME của tệp tin (ví dụ: image/jpeg, video/mp4)
 * @param byteSize            kích thước tệp tính bằng byte
 * @param sha256              mã băm SHA-256 của nội dung tệp, dùng để chống trùng lặp
 * @param originalFilename    tên tệp gốc do người dùng tải lên
 * @param status              trạng thái hiện tại trong vòng đời (xem {@link Status})
 * @param quarantinePath      đường dẫn lưu trữ tạm khi tài sản bị cách ly, null nếu không
 * @param promoted            cờ đánh dấu tài sản đã được thăng cấp (ví dụ: thành ảnh đại diện)
 * @param retentionHoldUntil  thời điểm hết hạn tạm giữ trước khi xóa vĩnh viễn, null nếu không
 * @param tombstonedAt        thời điểm xóa mềm, null nếu còn hoạt động
 * @param createdAt           thời điểm tạo bản ghi
 * @param updatedAt           thời điểm cập nhật bản ghi lần cuối
 * @param version             phiên bản của bản ghi, dùng cho optimistic locking
 */
public record MediaAsset(
        UUID id,
        UUID treeId,
        UUID albumId,
        UUID ownerUserId,
        Kind kind,
        String mimeType,
        long byteSize,
        String sha256,
        String originalFilename,
        Status status,
        String quarantinePath,
        boolean promoted,
        Instant retentionHoldUntil,
        Instant tombstonedAt,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    /**
     * Loại tài sản media.
     */
    public enum Kind {
        /** Ảnh tĩnh (JPEG, PNG, WebP, ...). */
        PHOTO,
        /** Video (MP4, MOV, ...). */
        VIDEO,
        /** Âm thanh (MP3, WAV, ...). */
        AUDIO,
        /** Tài liệu (PDF, DOCX, ...). */
        DOCUMENT,
        /** Các loại tệp khác chưa được phân loại cụ thể. */
        OTHER
    }

    /**
     * Trạng thái vòng đời của tài sản media.
     */
    public enum Status {
        /** Vừa được tải lên và đang được cách ly, chờ quét virus. */
        QUARANTINED,
        /** Đang trong quá trình quét virus/an toàn. */
        SCANNING,
        /** Đã sẵn sàng để sử dụng và phục vụ người dùng. */
        READY,
        /** Quét hoặc xử lý thất bại, tài sản không thể dùng được. */
        FAILED,
        /** Đã bị xóa mềm, không hiển thị nhưng vẫn còn trong cơ sở dữ liệu. */
        TOMBSTONED
    }
}
