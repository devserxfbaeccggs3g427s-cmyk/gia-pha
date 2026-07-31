package com.familya.search.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Tài liệu tìm kiếm cho một thành viên trong cây gia phả.
 *
 * <p>Bao gồm họ tên đầy đủ và các thành phần tên (tên đệm/họ) để vừa phục vụ
 * tìm kiếm theo chuỗi đầy đủ vừa cho phép truy vấn theo từng phần tên. Ngoài
 * ra còn có năm sinh/năm mất phục vụ lọc theo khoảng thời gian, và cờ
 * {@code tombstoned} để ẩn thành viên đã xoá mềm.</p>
 *
 * <p>Trường {@code normalized_name} được tính sẵn bằng {@code VietnameseNormalizer}
 * và dùng để truy vấn không phân biệt dấu.</p>
 *
 * @param treeId       định danh cây gia phả.
 * @param memberId     định danh thành viên.
 * @param fullName     họ tên đầy đủ hiển thị (giữ dấu).
 * @param givenName    tên đệm/tên gọi, có thể {@code null}.
 * @param surname      họ, có thể {@code null}.
 * @param birthYear    năm sinh, có thể {@code null} nếu chưa biết.
 * @param deathYear    năm mất, có thể {@code null} nếu còn sống hoặc chưa rõ.
 * @param tombstoned   {@code true} nếu thành viên đã bị xoá mềm.
 * @param lastUpdated  thời điểm cập nhật cuối trong bảng chiếu.
 */
public record MemberSearchDocument(
        UUID treeId,
        UUID memberId,
        String fullName,
        String givenName,
        String surname,
        Integer birthYear,
        Integer deathYear,
        boolean tombstoned,
        Instant lastUpdated
) { }
