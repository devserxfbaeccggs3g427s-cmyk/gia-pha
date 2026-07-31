package com.familya.member.application.port.in;

import java.util.UUID;

/**
 * Input for processing a Saga reply. The owner (Member service) is the only
 * consumer of these replies for the delete-member Saga.
 */
/**
 * Đầu vào cho bộ xử lý reply của Saga xóa thành viên. Member Service là consumer duy nhất
 * của các reply này.
 *
 * @param operationId           mã operationId của Saga
 * @param participantService    tên dịch vụ tham gia (relationship-service, event-service, ...)
 * @param stepCode              mã bước Saga đã hoàn tất
 * @param appliedAggregateVersion phiên bản aggregate đã áp dụng (cho barrier)
 * @param appliedEpoch          epoch đã áp dụng (cho barrier)
 * @param compensationApplied   {@code true} nếu đây là reply cho compensation
 * @param failed                {@code true} nếu bước thất bại
 * @param failureCode           mã lỗi (nếu có)
 * @param failureMessage        mô tả lỗi (nếu có)
 */
public record DeleteMemberSagaReplyCommand(
        UUID operationId,
        String participantService,
        String stepCode,
        long appliedAggregateVersion,
        long appliedEpoch,
        boolean compensationApplied,
        boolean failed,
        String failureCode,
        String failureMessage) {
}