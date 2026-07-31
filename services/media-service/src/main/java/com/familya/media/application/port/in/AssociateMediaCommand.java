package com.familya.media.application.port.in;

import java.util.UUID;

/**
 * Lệnh (command) đầu vào cho use case {@code AssociateMediaUseCase}.
 *
 * <p>Đây là một {@code port in} theo kiến trúc hexagonal: đóng gói dữ liệu
 * mà tầng application cần từ tầng adapter (REST, gRPC, Saga...) để gắn một
 * media đã ở trạng thái {@code READY} vào một đối tượng nghiệp vụ (member /
 * event / album) trong cùng một family-tree.</p>
 *
 * <p>Luồng nghiệp vụ chính:</p>
 * <ol>
 *   <li>Tra cứu {@code MediaAsset} theo {@link #mediaId()}.</li>
 *   <li>Kiểm tra quyền của {@link #actingUser()} trên tree chứa media.</li>
 *   <li>Đối chiếu {@link #expectedVersion()} với phiên bản hiện tại của
 *       media (optimistic concurrency).</li>
 *   <li>Đối chiếu {@link #expectedTreeRevision()} với projection phân
 *       quyền để phát hiện quyền bị thu hồi.</li>
 *   <li>Xác nhận {@link #targetKind()} / {@link #targetId()} đang khả dụng
 *       (qua {@code ReferenceAvailability}) rồi ghi bản ghi {@code ACTIVE}
 *       vào {@code MediaReferenceRepository}.</li>
 *   <li>Phát sự kiện {@code MediaAssociated} để downstream consume.</li>
 * </ol>
 *
 * @param mediaId              định danh media cần gắn liên kết; bắt buộc tồn tại.
 * @param actingUser           UUID người dùng thực hiện lệnh; dùng để truy
 *                             vấn quyền trên tree.
 * @param expectedVersion      phiên bản tốiển kỳ vọng của {@code MediaAsset}
 *                             (số nguyên tăng đơn điệu), dùng cho optimistic
 *                             concurrency; lệch sẽ ném
 *                             {@code OptimisticConcurrencyException}.
 * @param expectedTreeRevision revision của projection phân quyền mà lệnh
 *                             dựa vào; nhằm phát hiện quyền bị thu hồi
 *                             trong lúc xử lý.
 * @param targetKind           loại đối tượng đích, một trong {@code MEMBER},
 *                             {@code EVENT}, {@code ALBUM}; dùng để tra cứu
 *                             {@code ReferenceAvailability} tương ứng.
 * @param targetId             UUID của đối tượng đích cùng tree với media.
 */
public record AssociateMediaCommand(
        UUID mediaId,
        UUID actingUser,
        long expectedVersion,
        long expectedTreeRevision,
        String targetKind,
        UUID targetId) {
}
