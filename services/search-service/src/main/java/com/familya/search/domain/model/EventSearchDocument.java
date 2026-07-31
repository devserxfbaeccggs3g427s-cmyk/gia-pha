package com.familya.search.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Tài liệu tìm kiếm (search document) cho một sự kiện trong cây gia phả.
 *
 * <p>Đây là bản chiếu (projection) của sự kiện được lưu trong service
 * {@code event}, được ghi vào bảng {@code search_event_doc} bởi
 * {@code SearchProjectionConsumer} và truy vấn bởi {@code SearchEventsUseCase}.</p>
 *
 * <p>Trường {@code tombstoned} đánh dấu sự kiện đã bị xoá mềm; truy vấn có
 * thể lọc theo cờ này để ẩn/hiện các sự kiện đã xoá. {@code lastUpdated}
 * được dùng để sắp xếp kết quả theo độ mới.</p>
 *
 * @param treeId       định danh cây gia phả.
 * @param eventId      định danh sự kiện.
 * @param title        tiêu đề hiển thị của sự kiện.
 * @param startDate    ngày bắt đầu (có thể {@code null} với sự kiện không có ngày).
 * @param kind         phân loại sự kiện (sinh nhật, đám hỏi, ...); là chuỗi tự do.
 * @param tombstoned   {@code true} nếu sự kiện đã bị xoá mềm.
 * @param lastUpdated  thời điểm cập nhật lần cuối trong bảng chiếu.
 */
public record EventSearchDocument(
        UUID treeId,
        UUID eventId,
        String title,
        LocalDate startDate,
        String kind,
        boolean tombstoned,
        Instant lastUpdated
) { }
