package com.familya.platform.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.Map;

/**
 * Envelope lỗi ổn định cho toàn bộ API của nền tảng.
 *
 * <p>Các trường trong envelope:</p>
 * <ul>
 *   <li>{@code code} — mã lỗi ổn định theo máy (machine-stable). Client nên
 *       dựa vào trường này để xử lý logic, ví dụ {@code "validation.failed"}.</li>
 *   <li>{@code message} — thông điệp dành cho con người, có thể bản địa hoá.</li>
 *   <li>{@code traceId} — mã truy vết OpenTelemetry, luôn có trong môi trường
 *       production để hỗ trợ việc truy tìm log khi hỗ trợ khách hàng.</li>
 *   <li>{@code details} — bản đồ chi tiết tuỳ ý nhưng phải nằm trong allowlist
 *       để tránh lộ thông tin nhạy cảm (PII, stack trace...).</li>
 * </ul>
 *
 * <p>Lớp này được chú thích {@link JsonInclude} với chiến lược {@code NON_NULL}
 * nhằm đảm bảo các trường {@code null} không xuất hiện trong JSON trả về, giúp
 * phản hồi gọn gàng và dễ đọc hơn.</p>
 *
 * @author Family Tree Platform Team
 */
@JsonInclude(Include.NON_NULL)
public record ErrorResponse(
        String code,
        String message,
        String traceId,
        Map<String, Object> details
) {
    /**
     * Tiện ích tạo {@link ErrorResponse} không kèm chi tiết — phù hợp với
     * phần lớn các lỗi phổ biến chỉ cần ba trường {@code code}, {@code message}
     * và {@code traceId}.
     *
     * @param code    mã lỗi ổn định theo máy
     * @param message thông điệp lỗi cho người dùng
     * @param traceId mã truy vết (OpenTelemetry trace id)
     * @return {@link ErrorResponse} với trường {@code details} là {@code null}
     */
    public static ErrorResponse of(String code, String message, String traceId) {
        // Tạo envelope với details = null; JsonInclude.NON_NULL sẽ loại bỏ
        // trường này khỏi JSON trả về.
        return new ErrorResponse(code, message, traceId, null);
    }
}
