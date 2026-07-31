package com.familya.media.application.port.in;

import java.util.UUID;

/**
 * Lệnh (command) đầu vào cho use case {@code VerifyUploadedUseCase}.
 *
 * <p>Đây là {@code port in} của kiến trúc hexagonal: caller thông báo cho
 * media-service rằng blob đã được upload xong và cung cấp sha256 + byte
 * size thực tế để đối chiếu với intent ban đầu. Use case sẽ:</p>
 * <ol>
 *   <li>Tra cứu {@code MediaAsset} theo {@link #mediaId()}.</li>
 *   <li>Kiểm tra quyền của {@link #actingUser()} trên tree.</li>
 *   <li>Đối chiếu {@link #expectedVersion()} (optimistic concurrency).</li>
 *   <li>Đối chiếu {@link #sha256()} với sha256 đã lưu trong intent
 *       (so sánh không phân biệt hoa thường).</li>
 *   <li>Đối chiếu {@link #byteSize()} với byte size intent.</li>
 *   <li>Nếu khớp: chuyển trạng thái sang {@code SCANNING}, phát sự kiện
 *       {@code MediaQuarantined(VERIFIED_AWAITING_SCAN)} để pipeline scan
 *       tiếp tục.</li>
 * </ol>
 *
 * @param mediaId              UUID media cần xác minh.
 * @param actingUser           UUID người dùng thực hiện lệnh.
 * @param expectedVersion      phiên bản kỳ vọng của {@code MediaAsset};
 *                             lệch sẽ ném {@code OptimisticConcurrencyException}.
 * @param expectedTreeRevision revision phân quyền kỳ vọng.
 * @param sha256               SHA-256 hash thực tế của blob đã upload;
 *                             so sánh không phân biệt hoa thường.
 * @param byteSize             kích thước bytes thực tế; phải khớp
 *                             byte-size đã khai báo trong intent.
 */
public record VerifyUploadedCommand(
        UUID mediaId,
        UUID actingUser,
        long expectedVersion,
        long expectedTreeRevision,
        String sha256,
        long byteSize) {
}
