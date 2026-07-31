package com.familya.search.application.port.in;

import java.util.UUID;

/**
 * Truy vấn tìm kiếm thành viên gửi từ REST controller tới {@code SearchMembersUseCase}.
 *
 * <p>Mang đầy đủ tham số phục vụ kiểm tra quyền, đối chiếu chuỗi (so khớp
 * với {@code normalized_name}), áp bộ lọc năm sinh và giới hạn kết quả.</p>
 *
 * @param treeId               định danh cây gia phả cần tìm.
 * @param actingUser           người dùng đang thực hiện truy vấn.
 * @param expectedTreeRevision phiên bản cây mà client kỳ vọng.
 * @param q                    chuỗi truy vấn tự do (được chuẩn hoá ở use case).
 * @param birthYear            năm sinh chính xác hoặc {@code null}.
 * @param birthYearFrom        cận dưới năm sinh hoặc {@code null}.
 * @param birthYearTo          cận trên năm sinh hoặc {@code null}.
 * @param tombstoned           lọc theo trạng thái xoá mềm hoặc {@code null}.
 * @param limit                giới hạn kết quả; {@code null} = use case tự áp mặc định.
 */
public record SearchMembersQuery(
        UUID treeId,
        UUID actingUser,
        long expectedTreeRevision,
        String q,
        Integer birthYear,
        Integer birthYearFrom,
        Integer birthYearTo,
        Boolean tombstoned,
        Integer limit) {
}
