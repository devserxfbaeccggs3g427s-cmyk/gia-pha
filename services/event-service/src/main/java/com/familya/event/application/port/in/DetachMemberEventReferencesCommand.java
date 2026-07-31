package com.familya.event.application.port.in;

import java.util.UUID;

/**
 * Lệnh Saga gửi từ <b>member-service</b> khi thực hiện xóa một thành
 * viên. Nhiệm vụ của event-service là <b>gỡ tham chiếu</b> tới thành
 * viên đó khỏi mọi sự kiện liên quan, nhưng giữ nguyên bản thân sự
 * kiện.
 *
 * <p>Đây là bước {@code DETACH_EVENT_REFERENCES} trong delete-member
 * Saga. Khi Saga rollback, bước đối ứng là
 * {@link RestoreMemberEventReferencesCommand}.
 *
 * <p>Use case xử lý:
 * {@link com.familya.event.application.usecase.DetachMemberEventReferencesUseCase}.
 *
 * <h2>Hành vi kỳ vọng</h2>
 * <ul>
 *   <li>Nếu thành viên là {@code primaryMemberId}: chuyển thành
 *       {@code null} cho sự kiện đó.</li>
 *   <li>Nếu nằm trong {@code additionalMemberIds}: loại khỏi danh
 *       sách.</li>
 *   <li>Sự kiện không bị tombstone.</li>
 * </ul>
 *
 * @param operationId            định danh toàn Saga.
 * @param treeId                 cây gia phả chứa thành viên.
 * @param memberId               thành viên bị xóa.
 * @param targetAggregateVersion phiên bản mục tiêu.
 * @param targetEpoch            epoch mục tiêu.
 *
 * @author gia-pha platform
 */
public record DetachMemberEventReferencesCommand(
        UUID operationId,
        UUID treeId,
        UUID memberId,
        long targetAggregateVersion,
        long targetEpoch) {
}
