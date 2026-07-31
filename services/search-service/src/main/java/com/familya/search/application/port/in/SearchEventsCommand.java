package com.familya.search.application.port.in;

import java.time.LocalDate;

/**
 * Lệnh (command) mô tả một yêu cầu tìm kiếm sự kiện với các bộ lọc đi kèm.
 *
 * <p>Đây là kiểu "lệnh" (command) vì nó mang ý nghĩa yêu cầu thực hiện một
 * hành động tìm kiếm (kèm theo telemetry); cấu trúc dữ liệu bên trong
 * {@link EventFilter} cũng được dùng lại trong {@code SearchEventsUseCase}
 * để truyền bộ lọc xuống tầng persistence.</p>
 */
public record SearchEventsCommand() {
    /**
     * Bộ lọc cho truy vấn sự kiện. Mọi trường đều tuỳ chọn - khi {@code null}
     * thì điều kiện tương ứng không được áp dụng.
     *
     * @param kind       phân loại sự kiện cần lọc (so khớp chính xác).
     * @param from       ngày bắt đầu nhỏ nhất (bao gồm).
     * @param to         ngày kết thúc lớn nhất (bao gồm).
     * @param tombstoned cờ lọc theo trạng thái xoá mềm; {@code null} nghĩa là
     *                   không lọc theo trạng thái này.
     */
    public record EventFilter(String kind, LocalDate from, LocalDate to, Boolean tombstoned) { }
}
