package com.familya.media.application.port.in;

import java.time.Instant;
import java.util.UUID;

/**
 * Lệnh (command) đầu vào cho use case {@code CreateUploadIntentUseCase}.
 *
 * <p>Đây là {@code port in} của kiến trúc hexagonal: thể hiện yêu cầu
 * mở một "phiên upload" cho media mới. Use case sẽ:</p>
 * <ol>
 *   <li>Kiểm tra {@link #byteSize()} không vượt giới hạn cấu hình.</li>
 *   <li>Tra cứu quyền của {@link #actingUser()} trên {@link #treeId()}.</li>
 *   <li>Tạo bản ghi {@code MediaAsset} ở trạng thái {@code QUARANTINED} với
 *       một {@code exactPath} xác định trước (deterministic).</li>
 *   <li>Yêu cầu {@code BlobCapabilityIssuer} cấp một URL upload có chữ ký
 *       (signed PUT) có thời hạn; URL này chỉ trả về đúng một lần cho
 *       caller và không bao giờ được log/persist.</li>
 *   <li>Phát sự kiện {@code MediaQuarantined} để downstream theo dõi.</li>
 * </ol>
 *
 * @param treeId               UUID family-tree sở hữu media mới.
 * @param actingUser           UUID người dùng thực hiện lệnh; phải có
 *                             quyền upload trên tree.
 * @param expectedTreeRevision revision phân quyền mà lệnh dựa vào; chống
 *                             race với thu hồi quyền.
 * @param mimeType             MIME type do client khai báo (vd.
 *                             {@code image/jpeg}). Dùng để phân loại
 *                             {@code Kind} và để gateway xác minh khi
 *                             upload xong.
 * @param byteSize             kích thước bytes dự kiến; phải {@code > 0}
 *                             và không vượt {@code maxByteSize} cấu hình.
 * @param originalFilename     tên file gốc từ phía client; chỉ phục vụ
 *                             hiển thị / audit, không dùng để dựng path.
 * @param sha256               hash SHA-256 của nội dung dự kiến; được lưu
 *                             trên bản ghi media để đối chiếu khi xác
 *                             minh upload thành công.
 * @param clientStartedAt      mốc thời gian phía client khởi động upload;
 *                             phục vụ chẩn đoán độ trễ / audit; có thể
 *                             null nếu client không cung cấp.
 */
public record CreateUploadIntentCommand(
        UUID treeId,
        UUID actingUser,
        long expectedTreeRevision,
        String mimeType,
        long byteSize,
        String originalFilename,
        String sha256,
        Instant clientStartedAt) {
}
