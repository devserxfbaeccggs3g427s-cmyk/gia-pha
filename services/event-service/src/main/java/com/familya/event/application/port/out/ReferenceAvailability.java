package com.familya.event.application.port.out;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Cổng ra cung cấp khả năng kiểm tra <b>tính khả dụng</b> của các tham
 * chiếu mờ (opaque ID) tới thành viên và media trong một cây gia phả.
 *
 * <h2>Bối cảnh</h2>
 * <p>Trong hệ thống gia-pha, mỗi microservice giữ bounded context riêng
 * và không nắm giữ bảng nguồn của dịch vụ khác. Thay vào đó, event-service
 * <i>tiêu thụ</i> các event stream {@code member.events.v1} /
 * {@code media.events.v1} để dựng các projection cục bộ:
 * <ul>
 *   <li>{@code member_reference_projection}</li>
 *   <li>{@code media_reference_projection}</li>
 * </ul>
 *
 * <p>Mọi tham chiếu chéo (memberId, mediaId) đều phải được xác thực qua
 * projection này trước khi sử dụng — đảm bảo <b>tính nhất quán cuối cùng</b>
 * và tránh khóa ngoại xuyên dịch vụ (cross-service FK is forbidden).
 *
 * <p>Triển khai: {@link com.familya.event.adapter.out.projection.JdbcProjectionAdapter}.
 *
 * @author gia-pha platform
 */
public interface ReferenceAvailability {

    /**
     * Cho biết một thành viên có <i>còn tồn tại</i> trong cây hay không.
     *
     * @param treeId   cây gia phả.
     * @param memberId ID thành viên.
     * @return {@code true} nếu thành viên tồn tại và chưa bị tombstone.
     */
    boolean isMemberAvailable(UUID treeId, UUID memberId);

    /**
     * Cho biết một media có <i>còn tồn tại</i> trong cây hay không.
     *
     * @param treeId  cây gia phả.
     * @param mediaId ID media.
     * @return {@code true} nếu media tồn tại và chưa bị tombstone.
     */
    boolean isMediaAvailable(UUID treeId, UUID mediaId);

    /**
     * Trả về tập các <b>tham chiếu đứt (dangling)</b> trong tập thành
     * viên đầu vào. Được dùng bởi endpoint đối chiếu (reconciliation)
     * để bề mặt các tham chiếu lỗi thời thay vì từ chối thẳng.
     *
     * @param treeId    cây gia phả.
     * @param memberIds tập ID cần kiểm tra.
     * @return tập (con của {@code memberIds}) các ID không khả dụng.
     */
    Set<UUID> danglingMembers(UUID treeId, Collection<UUID> memberIds);

    /**
     * Trả về tập các <b>tham chiếu đứt (dangling)</b> trong tập media.
     *
     * @param treeId   cây gia phả.
     * @param mediaIds tập ID cần kiểm tra.
     * @return tập (con của {@code mediaIds}) các ID không khả dụng.
     */
    Set<UUID> danglingMedia(UUID treeId, Collection<UUID> mediaIds);
}
