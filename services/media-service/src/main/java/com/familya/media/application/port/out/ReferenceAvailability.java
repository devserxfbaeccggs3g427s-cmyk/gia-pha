package com.familya.media.application.port.out;

import java.util.UUID;

/**
 * Member projection availability. Used by
 * {@code AssociateMediaUseCase} for {@code AVATAR} targets: an avatar
 * must point at a member that is alive in the projection. Returns
 * false when missing.
 *
 * <p>Port ra (driven port) của kiến trúc hexagonal: truy vấn nhanh
 * "đối tượng này còn tồn tại trong projection không?". Triển khai đọc
 * từ các projection đã populate sẵn từ các topic sự kiện của member /
 * event / album services.</p>
 */
public interface ReferenceAvailability {

    /**
     * Kiểm tra member có còn "sống" trong projection không.
     *
     * @param treeId   UUID family-tree.
     * @param memberId UUID member.
     * @return {@code true} nếu member tồn tại và chưa bị xóa; ngược lại
     *         {@code false} (kể cả khi không có trong projection).
     */
    boolean isMemberAvailable(UUID treeId, UUID memberId);

    /**
     * Kiểm tra event có còn trong projection không.
     *
     * @param treeId  UUID family-tree.
     * @param eventId UUID event.
     * @return {@code true} nếu event tồn tại; ngược lại {@code false}.
     */
    boolean isEventAvailable(UUID treeId, UUID eventId);

    /**
     * Kiểm tra album có còn trong projection không.
     *
     * @param treeId   UUID family-tree.
     * @param albumId  UUID album.
     * @return {@code true} nếu album tồn tại và chưa tombstone; ngược
     *         lại {@code false}.
     */
    boolean isAlbumAvailable(UUID treeId, UUID albumId);
}
