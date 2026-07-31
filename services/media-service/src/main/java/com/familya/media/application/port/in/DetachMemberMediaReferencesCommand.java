package com.familya.media.application.port.in;

import java.util.UUID;

/**
 * Saga command from the Member service's delete-member Saga. Detaches every
 * MEMBER-kind reference that points to the deleted member. Physical binary
 * deletion is NOT part of this Saga and is performed by the delayed cleanup
 * worker under retention holds.
 *
 * <p>Đây là {@code port in} của kiến trúc hexagonal, được {@code Member}
 * Saga gửi tới media-service. Use case {@code DetachMemberMediaReferencesUseCase}
 * sẽ:</p>
 * <ol>
 *   <li>Liệt kê tất cả tham chiếu loại {@code MEMBER} trỏ tới
 *       {@link #memberId()} trong {@link #treeId()}.</li>
 *   <li>Lưu một snapshot bù trừ (compensation snapshot) theo
 *       {@link #operationId()} để có thể khôi phục khi Saga fail.</li>
 *   <li>Xóa các tham chiếu đó khỏi {@code MediaReferenceRepository}.</li>
 * </ol>
 *
 * <p>Quan trọng: use case KHÔNG xóa binary vật lý; binary sẽ được cleanup
 * worker xóa sau khi retention hold hết hạn.</p>
 *
 * @param operationId           UUID định danh Saga; dùng để tra snapshot
 *                              bù trừ khi rollback.
 * @param treeId                UUID family-tree chứa member bị xóa.
 * @param memberId              UUID member bị xóa, là đích của các tham
 *                              chiếu cần tách ra.
 * @param targetAggregateVersion phiên bản aggregate dự kiến tại thời
 *                              điểm Saga commit; dùng để xác định tính
 *                              tuyến tính của lệnh.
 * @param targetEpoch           epoch của Saga; dùng để phân biệt các
 *                              lần chạy lại với cùng {@code operationId}.
 */
public record DetachMemberMediaReferencesCommand(
        UUID operationId,
        UUID treeId,
        UUID memberId,
        long targetAggregateVersion,
        long targetEpoch) {
}