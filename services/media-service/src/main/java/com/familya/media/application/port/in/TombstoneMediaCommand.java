package com.familya.media.application.port.in;

import java.time.Instant;
import java.util.UUID;

/**
 * Lệnh (command) đầu vào cho use case {@code TombstoneMediaUseCase}.
 *
 * <p>Đây là {@code port in} của kiến trúc hexagonal: yêu cầu đánh dấu
 * media là "đã xóa mềm" (tombstone) và đặt retention hold để binary chỉ
 * bị cleanup worker xóa sau khi hết hạn. Đây là bước trước ranh giới
 * không-thể-đảo (irreversible boundary) trong quy trình xóa media.</p>
 *
 * <p>Luồng nghiệp vụ chính:</p>
 * <ol>
 *   <li>Tra cứu {@code MediaAsset} theo {@link #mediaId()}.</li>
 *   <li>Kiểm tra quyền của {@link #actingUser()} trên tree.</li>
 *   <li>Đối chiếu {@link #expectedVersion()} (optimistic concurrency).</li>
 *   <li>Tombstone media, đặt retention hold với deadline
 *       {@link #retentionHoldUntil()} (mặc định 30 ngày nếu null).</li>
 *   <li>Phát sự kiện {@code MediaDetached} để downstream vô hiệu hóa
 *       tham chiếu.</li>
 * </ol>
 *
 * @param mediaId              UUID media cần tombstone.
 * @param actingUser           UUID người dùng thực hiện lệnh; phải có
 *                             quyền delete trên tree.
 * @param expectedVersion      phiên bản kỳ vọng của {@code MediaAsset};
 *                             lệch sẽ ném {@code OptimisticConcurrencyException}.
 * @param expectedTreeRevision revision phân quyền kỳ vọng; chống race
 *                             với thu hồi quyền trong lúc xử lý.
 * @param retentionHoldUntil   deadline giữ binary; nếu {@code null} use
 *                             case sẽ dùng mặc định 30 ngày kể từ hiện
 *                             tại. Sau thời điểm này cleanup worker có
 *                             thể xóa binary.
 */
public record TombstoneMediaCommand(
        UUID mediaId,
        UUID actingUser,
        long expectedVersion,
        long expectedTreeRevision,
        Instant retentionHoldUntil) {
}
