package com.familya.search.application.port.in;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Truy vấn tìm kiếm sự kiện gửi từ REST controller tới {@code SearchEventsUseCase}.
 *
 * <p>Bao gồm đầy đủ tham số cần thiết để:</p>
 * <ul>
 *   <li>Kiểm tra quyền (treeId, actingUser, expectedTreeRevision).</li>
 *   <li>Chuẩn hoá và đối chiếu chuỗi truy vấn ({@code q}).</li>
 *   <li>Áp dụng bộ lọc theo {@code kind}, khoảng ngày, cờ tombstoned.</li>
 *   <li>Giới hạn kết quả trả về.</li>
 * </ul>
 *
 * @param treeId               định danh cây gia phả cần tìm.
 * @param actingUser           người dùng đang thực hiện truy vấn.
 * @param expectedTreeRevision phiên bản cây mà client kỳ vọng.
 * @param q                    chuỗi truy vấn tự do (đã được chuẩn hoá ở use case).
 * @param kind                 phân loại sự kiện (so khớp chính xác) hoặc {@code null}.
 * @param from                 cận dưới ngày bắt đầu hoặc {@code null}.
 * @param to                   cận trên ngày bắt đầu hoặc {@code null}.
 * @param tombstoned           lọc theo trạng thái xoá mềm hoặc {@code null}.
 * @param limit                giới hạn kết quả; {@code null} = use case tự áp mặc định.
 */
public record SearchEventsQuery(
        UUID treeId,
        UUID actingUser,
        long expectedTreeRevision,
        String q,
        String kind,
        LocalDate from,
        LocalDate to,
        Boolean tombstoned,
        Integer limit) {
}
