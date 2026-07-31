package com.familya.event.application.port.in;

import com.familya.event.domain.model.DomainEvent;
import com.familya.event.domain.model.RecurrenceRule;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Lệnh (command) đầu vào cho use case
 * {@link com.familya.event.application.usecase.CreateDomainEventUseCase}.
 *
 * <p>Đây là một {@code record} bất biến — mọi thuộc tính được xác định
 * tại thời điểm tạo và không thể thay đổi. Đối tượng này được khởi tạo
 * ở REST adapter (xem
 * {@link com.familya.event.adapter.in.rest.DomainEventController}) và
 * chuyển xuống tầng application, tuân theu nguyên tắc <i>CQRS</i> /
 * <i>Application Service</i>.
 *
 * <h2>Các khái niệm</h2>
 * <ul>
 *   <li><b>actingUser</b>: người dùng thực hiện hành động — dùng để tra
 *       cứu quyền trên projection {@code authorization_projection}.</li>
 *   <li><b>expectedTreeRevision</b>: revision cây gia phả kỳ vọng —
 *       đảm bảo quyết định phân quyền dựa trên projection đủ mới.</li>
 *   <li><b>recurrence</b>: quy tắc lặp lại (có thể {@code null} nếu sự
 *       kiện chỉ xảy ra một lần).</li>
 * </ul>
 *
 * @param treeId                định danh cây gia phả — bắt buộc.
 * @param actingUser            định danh người dùng — bắt buộc.
 * @param title                 tiêu đề sự kiện — bắt buộc.
 * @param description           mô tả chi tiết — có thể {@code null}.
 * @param kind                  loại sự kiện — bắt buộc.
 * @param startDate             ngày bắt đầu — có thể {@code null}.
 * @param endDate               ngày kết thúc — phải {@code >= startDate} nếu cùng khác {@code null}.
 * @param recurrence            quy tắc lặp lại — có thể {@code null}.
 * @param primaryMemberId       ID thành viên chính — có thể {@code null}.
 * @param additionalMemberIds   danh sách ID thành viên phụ — có thể {@code null}.
 * @param mediaRefs             danh sách ID media — có thể {@code null}.
 * @param location              địa điểm — có thể {@code null}.
 * @param expectedTreeRevision  revision cây kỳ vọng — bắt buộc.
 *
 * @author gia-pha platform
 */
public record CreateDomainEventCommand(
        UUID treeId,
        UUID actingUser,
        String title,
        String description,
        DomainEvent.Kind kind,
        LocalDate startDate,
        LocalDate endDate,
        RecurrenceRule recurrence,
        UUID primaryMemberId,
        List<UUID> additionalMemberIds,
        List<UUID> mediaRefs,
        String location,
        long expectedTreeRevision
) { }
