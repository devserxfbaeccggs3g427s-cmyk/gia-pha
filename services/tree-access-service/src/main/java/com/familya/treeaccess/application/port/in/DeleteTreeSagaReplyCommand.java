package com.familya.treeaccess.application.port.in;

import java.util.UUID;

/**
 * Lệnh đại diện cho phản hồi Saga delete-tree từ một tham gia viên.
 *
 * @param operationId          mã thao tác Saga
 * @param participantService   tên service tham gia
 * @param stepCode             mã bước forward mà reply đề cập
 * @param appliedAggregateVersion phiên bản tổng hợp mà tham gia viên đã áp dụng
 * @param appliedEpoch         epoch đã áp dụng
 * @param compensationApplied  {@code true} nếu reply là kết quả compensation
 * @param failed               {@code true} nếu bước thất bại
 * @param failureCode          mã lỗi hoặc {@code null}
 * @param failureMessage       thông điệp lỗi hoặc {@code null}
 */
public record DeleteTreeSagaReplyCommand(
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