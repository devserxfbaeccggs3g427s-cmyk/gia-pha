package com.familya.event.application.port.in;

import com.familya.event.domain.model.DomainEvent;
import com.familya.event.domain.model.RecurrenceRule;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Lệnh cập nhật nội dung của một
 * {@link com.familya.event.domain.model.DomainEvent sự kiện gia phả}
 * đã tồn tại.
 *
 * <p>Được xử lý bởi use case
 * {@link com.familya.event.application.usecase.UpdateDomainEventUseCase}.
 *
 * <h2>Cơ chế đồng bộ</h2>
 * <ul>
 *   <li>{@code expectedVersion}: phiên bản tương tranh lạc quan của
 *       aggregate, thường được truyền qua header {@code If-Match}.</li>
 *   <li>{@code expectedTreeRevision}: revision cây kỳ vọng dùng cho
 *       phân quyền; thường truyền qua header {@code X-Tree-Revision}.</li>
 * </ul>
 *
 * @param eventId               định danh sự kiện cần cập nhật.
 * @param actingUser            người dùng thực hiện.
 * @param expectedVersion       phiên bản aggregate kỳ vọng (If-Match).
 * @param expectedTreeRevision  revision cây kỳ vọng.
 * @param title                 tiêu đề mới.
 * @param description           mô tả mới.
 * @param kind                  loại sự kiện mới.
 * @param startDate             ngày bắt đầu mới.
 * @param endDate               ngày kết thúc mới.
 * @param recurrence            quy tắc lặp lại mới.
 * @param primaryMemberId       ID thành viên chính mới.
 * @param additionalMemberIds   danh sách thành viên phụ mới.
 * @param mediaRefs             danh sách media mới.
 * @param location              địa điểm mới.
 *
 * @author gia-pha platform
 */
public record UpdateDomainEventCommand(
        UUID eventId,
        UUID actingUser,
        long expectedVersion,
        long expectedTreeRevision,
        String title,
        String description,
        DomainEvent.Kind kind,
        LocalDate startDate,
        LocalDate endDate,
        RecurrenceRule recurrence,
        UUID primaryMemberId,
        List<UUID> additionalMemberIds,
        List<UUID> mediaRefs,
        String location
) { }
