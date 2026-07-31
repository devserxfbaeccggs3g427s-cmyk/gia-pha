package com.familya.search.application.port.in;

/**
 * Lệnh (command) mô tả yêu cầu tìm kiếm media với bộ lọc đi kèm.
 *
 * <p>Tương tự {@code SearchEventsCommand}, đây là kiểu "lệnh" được dùng lại
 * để vừa truyền qua REST, vừa truyền bộ lọc xuống tầng persistence thông qua
 * {@link MediaFilter}.</p>
 */
public record SearchMediaCommand() {
    /**
     * Bộ lọc cho truy vấn media.
     *
     * @param kind       phân loại media cần lọc hoặc {@code null}.
     * @param tombstoned cờ lọc theo trạng thái xoá mềm hoặc {@code null}.
     */
    public record MediaFilter(String kind, Boolean tombstoned) { }
}
