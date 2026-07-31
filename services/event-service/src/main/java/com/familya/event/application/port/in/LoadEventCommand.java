package com.familya.event.application.port.in;

import com.familya.event.domain.model.DomainEvent;
import com.familya.event.domain.model.RecurrenceRule;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Đầu vào cho use case
 * {@link com.familya.event.application.usecase.LoadEventUseCase}
 * — dùng cho quá trình <b>di trú dữ liệu (data migration)</b> từ các
 * manifest blob bất biến.
 *
 * <p>Quá trình di trú yêu cầu giữ nguyên:
 * <ul>
 *   <li>{@code eventId} ban đầu (do manifest cấp).</li>
 *   <li>{@code createdAt}, {@code updatedAt} ban đầu.</li>
 *   <li>Cờ tombstone ({@code tombstoned}) ban đầu.</li>
 * </ul>
 *
 * <p>Cờ {@code replaySafe} cho biết liệu bản ghi có thể được nạp lại
 * nhiều lần mà không tạo trùng lặp (idempotent). Khi {@code true}, use
 * case sẽ bỏ qua bản ghi đã tồn tại thay vì ném lỗi.
 *
 * @param eventId             định danh ban đầu của sự kiện (giữ nguyên).
 * @param treeId              định danh cây gia phả.
 * @param title               tiêu đề.
 * @param description         mô tả.
 * @param kind                loại sự kiện.
 * @param startDate           ngày bắt đầu.
 * @param endDate             ngày kết thúc.
 * @param recurrence          quy tắc lặp lại.
 * @param primaryMemberId     ID thành viên chính.
 * @param additionalMemberIds danh sách thành viên phụ.
 * @param mediaRefs           danh sách media.
 * @param location            địa điểm.
 * @param createdAt           thời điểm tạo ban đầu (giữ nguyên).
 * @param updatedAt           thời điểm cập nhật ban đầu.
 * @param tombstoned          cờ tombstone ban đầu.
 * @param replaySafe          cho biết có thể nạp lặp lại idempotent.
 *
 * @author gia-pha platform
 */
public record LoadEventCommand(
        UUID eventId,
        UUID treeId,
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
        java.time.Instant createdAt,
        java.time.Instant updatedAt,
        boolean tombstoned,
        boolean replaySafe
) { }
