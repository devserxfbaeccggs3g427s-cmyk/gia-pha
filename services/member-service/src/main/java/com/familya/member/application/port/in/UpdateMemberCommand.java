package com.familya.member.application.port.in;

import com.familya.member.domain.model.Member;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Đầu vào cho use case cập nhật thông tin một thành viên. Tất cả các trường thông tin đều
 * có thể null để hỗ trợ cập nhật một phần.
 *
 * @param memberId            mã thành viên cần cập nhật
 * @param actingUser          người dùng thực hiện
 * @param expectedVersion     phiên bản kỳ vọng của thành viên
 * @param expectedTreeRevision phiên bản cây kỳ vọng
 * @param displayName         tên hiển thị mới (tùy chọn)
 * @param givenName           tên mới
 * @param surname             họ mới
 * @param birthDate           ngày sinh mới
 * @param deathDate           ngày mất mới
 * @param gender              giới tính mới
 * @param generation          thế hệ mới
 * @param notes               ghi chú mới
 */
public record UpdateMemberCommand(
        UUID memberId,
        UUID actingUser,
        long expectedVersion,
        long expectedTreeRevision,
        String displayName,
        String givenName,
        String surname,
        LocalDate birthDate,
        LocalDate deathDate,
        Member.Gender gender,
        Integer generation,
        String notes
) { }