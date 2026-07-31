package com.familya.media.application.port.in;

import java.util.UUID;

/**
 * Lệnh (command) đầu vào cho use case {@code RestoreMemberMediaReferencesUseCase}.
 *
 * <p>Đây là {@code port in} của kiến trúc hexagonal, dùng trong bước bù
 * trừ (compensation) của delete-member Saga khi Saga fail. Use case sẽ
 * phát lại các tham chiếu loại {@code MEMBER} đã bị xóa bởi
 * {@code DetachMemberMediaReferencesUseCase}, dựa trên snapshot đã lưu
 * theo {@link #operationId()}.</p>
 *
 * @param operationId UUID định danh Saga cần bù trừ; dùng để tra snapshot
 *                    bù trừ trong {@code MediaReferenceRepository}.
 * @param treeId      UUID family-tree chứa member được khôi phục.
 * @param memberId    UUID member được khôi phục; chỉ phát lại các tham
 *                    chiếu trỏ tới member này.
 */
public record RestoreMemberMediaReferencesCommand(
        UUID operationId,
        UUID treeId,
        UUID memberId) {
}