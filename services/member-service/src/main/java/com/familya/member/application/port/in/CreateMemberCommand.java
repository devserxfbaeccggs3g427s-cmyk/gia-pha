package com.familya.member.application.port.in;

import com.familya.member.domain.model.Member;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Lệnh (command) tạo mới một thành viên. Đây là một {@code record} thuộc tầng application/port-in
 * trong kiến trúc Hexagonal — đóng gói đầu vào cho {@link com.familya.member.application.usecase.CreateMemberUseCase}.
 *
 * @param treeId               mã cây chứa thành viên
 * @param actingUser           người dùng thực hiện hành động
 * @param userId               mã người dùng hệ thống (có thể null nếu thành viên chưa liên kết tài khoản)
 * @param displayName          tên hiển thị
 * @param givenName            tên
 * @param surname              họ
 * @param birthDate            ngày sinh
 * @param deathDate            ngày mất
 * @param birthYearKnown       năm sinh đã biết chính xác
 * @param deathYearKnown       năm mất đã biết chính xác
 * @param gender               giới tính
 * @param status               trạng thái (LIVING/DECEASED/...)
 * @param generation           thế hệ trong cây
 * @param legacyAvatarUrl      URL ảnh đại diện cũ (tương thích ngược)
 * @param notes                ghi chú tự do
 * @param expectedTreeRevision phiên bản cây kỳ vọng dùng cho optimistic concurrency
 */
public record CreateMemberCommand(
        UUID treeId,
        UUID actingUser,
        UUID userId,
        String displayName,
        String givenName,
        String surname,
        LocalDate birthDate,
        LocalDate deathDate,
        boolean birthYearKnown,
        boolean deathYearKnown,
        Member.Gender gender,
        Member.Status status,
        Integer generation,
        String legacyAvatarUrl,
        String notes,
        long expectedTreeRevision
) { }