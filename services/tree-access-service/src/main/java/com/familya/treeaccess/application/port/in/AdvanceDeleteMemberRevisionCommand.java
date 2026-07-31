package com.familya.treeaccess.application.port.in;

import java.util.UUID;

/**
 * Saga command from the Member service's delete-member Saga. Advances the
 * authoritative tree revision and epoch after every other participant has
 * acked. The tree is allowed to be in FROZEN or PENDING_DELETION state.
 */
/**
 * Lệnh Saga từ {@code member-service} (delete-member Saga) yêu cầu tăng
 * revision/epoch của cây sau khi tất cả các tham gia viên khác đã ACK.
 * Cây được phép ở trạng thái FROZEN hoặc PENDING_DELETION.
 *
 * @param operationId        mã thao tác Saga tương ứng
 * @param treeId             mã cây cần cập nhật
 * @param expectedTreeVersion phiên bản tối thiểu kỳ vọng (kiểm soát tương tranh)
 * @param newRevision        revision mục tiêu
 * @param newEpoch           epoch mục tiêu
 */
public record AdvanceDeleteMemberRevisionCommand(
        UUID operationId,
        UUID treeId,
        long expectedTreeVersion,
        long newRevision,
        long newEpoch) {
}