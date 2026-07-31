package com.familya.media.application.port.in;

import java.util.UUID;

/**
 * Lệnh (command) đầu vào cho use case {@code CreateAlbumUseCase}.
 *
 * <p>Đây là {@code port in} của kiến trúc hexagonal: chứa toàn bộ tham số
 * cần thiết để tầng application tạo mới một {@code Album} trong một
 * family-tree. Album vừa là nhóm media, vừa là target cho các tham chiếu
 * media (kiểu {@code ALBUM}).</p>
 *
 * <p>Luồng nghiệp vụ chính:</p>
 * <ol>
 *   <li>Tra cứu quyền của {@link #actingUser()} trên {@link #treeId()}.</li>
 *   <li>Đối chiếu {@link #expectedTreeRevision()} với projection phân
 *       quyền hiện tại (phát hiện quyền bị thu hồi).</li>
 *   <li>Tạo {@code Album} mới với version {@code 0}, ghi vào
 *       {@code AlbumRepository}.</li>
 *   <li>Phát sự kiện {@code AlbumCreated} để các bên liên quan (search
 *       index, projection) cập nhật.</li>
 * </ol>
 *
 * @param treeId               UUID của family-tree chứa album.
 * @param actingUser           UUID người dùng thực hiện lệnh; phải có vai
 *                             trò {@code ADMIN}/{@code EDITOR} trên tree.
 * @param expectedTreeRevision revision phân quyền mà lệnh dựa vào; dùng để
 *                             chống lại tình trạng quyền bị thu hồi
 *                             trong lúc xử lý.
 * @param name                 tên hiển thị của album; bắt buộc khác null
 *                             và không rỗng (do domain validate).
 * @param description          mô tả album; có thể null hoặc rỗng.
 * @param coverMediaId         UUID media dùng làm ảnh bìa album; có thể
 *                             null nếu album chưa có ảnh bìa. Khi khác
 *                             null media phải thuộc cùng tree và đang ở
 *                             trạng thái {@code READY}.
 */
public record CreateAlbumCommand(
        UUID treeId,
        UUID actingUser,
        long expectedTreeRevision,
        String name,
        String description,
        UUID coverMediaId) {
}
