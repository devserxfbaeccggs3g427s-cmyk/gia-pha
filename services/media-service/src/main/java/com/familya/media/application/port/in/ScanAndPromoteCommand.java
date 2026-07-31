package com.familya.media.application.port.in;

import java.util.UUID;

/**
 * Lệnh (command) đầu vào cho use case {@code ScanAndPromoteUseCase}.
 *
 * <p>Đây là {@code port in} của kiến trúc hexagonal: yêu cầu quét virus
 * và (nếu sạch) chuyển trạng thái media sang {@code READY}. Được gọi
 * bởi caller đã upload xong và đã verify checksum (sha256, byte size)
 * thành công.</p>
 *
 * <p>Luồng nghiệp vụ chính:</p>
 * <ol>
 *   <li>Tra cứu {@code MediaAsset} theo {@link #mediaId()}.</li>
 *   <li>Kiểm tra quyền của {@link #actingUser()} trên tree chứa media.</li>
 *   <li>Đối chiếu {@link #expectedVersion()} (optimistic concurrency).</li>
 *   <li>Đảm bảo media đang ở trạng thái {@code QUARANTINED} hoặc
 *       {@code SCANNING} (đã qua verify).</li>
 *   <li>Gọi {@code ScannerGateway} (fail-closed: lỗi → {@code FAILED}).</li>
 *   <li>Nếu {@code CLEAN}: chuyển {@code READY}, phát {@code MediaScanned}
 *       và {@code MediaActivated}, ghi hàng đợi replicate sang region
 *       phụ.</li>
 * </ol>
 *
 * @param mediaId              UUID media cần quét và promote.
 * @param actingUser           UUID người dùng thực hiện lệnh; phải có
 *                             quyền scan trên tree.
 * @param expectedVersion      phiên bản kỳ vọng của {@code MediaAsset};
 *                             lệch sẽ ném {@code OptimisticConcurrencyException}.
 * @param expectedTreeRevision revision phân quyền kỳ vọng; chống race
 *                             với thu hồi quyền trong lúc xử lý.
 */
public record ScanAndPromoteCommand(
        UUID mediaId,
        UUID actingUser,
        long expectedVersion,
        long expectedTreeRevision) {
}
