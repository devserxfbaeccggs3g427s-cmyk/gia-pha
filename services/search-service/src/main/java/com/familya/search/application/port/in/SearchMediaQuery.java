package com.familya.search.application.port.in;

import java.util.UUID;

/**
 * Truy vấn tìm kiếm media gửi từ REST controller tới {@code SearchMediaUseCase}.
 *
 * <p>Tương tự {@code SearchEventsQuery}, mang đầy đủ tham số phục vụ kiểm
 * tra quyền, đối chiếu chuỗi, áp bộ lọc và giới hạn kết quả.</p>
 *
 * @param treeId               định danh cây gia phả cần tìm.
 * @param actingUser           người dùng đang thực hiện truy vấn.
 * @param expectedTreeRevision phiên bản cây mà client kỳ vọng.
 * @param q                    chuỗi truy vấn tự do (được chuẩn hoá ở use case).
 * @param kind                 phân loại media hoặc {@code null}.
 * @param tombstoned           lọc theo trạng thái xoá mềm hoặc {@code null}.
 * @param limit                giới hạn kết quả; {@code null} = use case tự áp mặc định.
 */
public record SearchMediaQuery(
        UUID treeId,
        UUID actingUser,
        long expectedTreeRevision,
        String q,
        String kind,
        Boolean tombstoned,
        Integer limit) {
}
