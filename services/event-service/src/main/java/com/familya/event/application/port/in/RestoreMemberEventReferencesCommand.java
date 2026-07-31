package com.familya.event.application.port.in;

import java.util.UUID;

/**
 * Lệnh Saga <b>khôi phục</b> các tham chiếu thành viên đã bị gỡ bởi
 * {@link DetachMemberEventReferencesCommand}.
 *
 * <p>Quy trình:
 * <ol>
 *   <li>Use case {@code DetachMemberEventReferencesUseCase} lưu một
 *       snapshot bù ({@code compensation snapshot}) gắn với
 *       {@code operationId}.</li>
 *   <li>Khi Saga rollback, lệnh này được phát để yêu cầu khôi phục
 *       các tham chiếu dựa trên snapshot.</li>
 * </ol>
 *
 * <p>Use case xử lý:
 * {@link com.familya.event.application.usecase.RestoreMemberEventReferencesUseCase}.
 *
 * @param operationId định danh toàn Saga (dùng để tra snapshot).
 * @param treeId      định danh cây gia phả.
 * @param memberId    ID thành viên cần khôi phục tham chiếu.
 *
 * @author gia-pha platform
 */
public record RestoreMemberEventReferencesCommand(
        UUID operationId,
        UUID treeId,
        UUID memberId) {
}
