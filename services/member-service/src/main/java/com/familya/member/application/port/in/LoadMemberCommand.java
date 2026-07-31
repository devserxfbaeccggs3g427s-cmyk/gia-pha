package com.familya.member.application.port.in;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Migration loader input. Members are loaded from immutable Blob
 * manifests with preserved IDs and timestamps. The loader is idempotent
 * per memberId; re-running does not duplicate effects.
 */
/**
 * Đầu vào cho bộ nạp (loader) dùng trong di trú. Thành viên được tải từ manifest bất biến
 * với ID và timestamp gốc; việc gọi lại với cùng {@code memberId} là idempotent và không
 * tạo hiệu ứng trùng lặp.
 *
 * @param memberId        mã thành viên gốc cần nạp
 * @param treeId          mã cây
 * @param userId          mã người dùng hệ thống (nếu có)
 * @param displayName     tên hiển thị
 * @param givenName       tên
 * @param surname         họ
 * @param birthDate       ngày sinh
 * @param deathDate       ngày mất
 * @param birthYearKnown  năm sinh đã biết chính xác
 * @param deathYearKnown  năm mất đã biết chính xác
 * @param gender          giới tính
 * @param status          trạng thái
 * @param generation      thế hệ
 * @param legacyAvatarUrl URL ảnh cũ
 * @param notes           ghi chú
 * @param createdAt       thời điểm tạo gốc (bảo toàn)
 * @param updatedAt       thời điểm cập nhật gốc
 * @param tombstoned      cờ đã tombstone hay chưa
 * @param replaySafe      cờ idempotency khi phát lại
 */
public record LoadMemberCommand(
        UUID memberId,
        UUID treeId,
        UUID userId,
        String displayName,
        String givenName,
        String surname,
        LocalDate birthDate,
        LocalDate deathDate,
        boolean birthYearKnown,
        boolean deathYearKnown,
        String gender,
        String status,
        Integer generation,
        String legacyAvatarUrl,
        String notes,
        java.time.Instant createdAt,
        java.time.Instant updatedAt,
        boolean tombstoned,
        boolean replaySafe
) { }