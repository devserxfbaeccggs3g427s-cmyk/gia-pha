package com.familya.search.application.port.in;

/**
 * Lệnh (command) mô tả yêu cầu tìm kiếm thành viên với bộ lọc đi kèm.
 *
 * <p>Cấu trúc dữ liệu {@link MemberFilter} được dùng chung giữa REST và
 * tầng persistence.</p>
 */
public record SearchMembersCommand() {
    /**
     * Bộ lọc cho truy vấn thành viên.
     *
     * @param birthYear      năm sinh chính xác cần lọc hoặc {@code null}.
     * @param birthYearFrom  cận dưới năm sinh (bao gồm) hoặc {@code null}.
     * @param birthYearTo    cận trên năm sinh (bao gồm) hoặc {@code null}.
     * @param tombstoned     cờ lọc theo trạng thái xoá mềm hoặc {@code null}.
     */
    public record MemberFilter(Integer birthYear, Integer birthYearFrom, Integer birthYearTo, Boolean tombstoned) { }
}
