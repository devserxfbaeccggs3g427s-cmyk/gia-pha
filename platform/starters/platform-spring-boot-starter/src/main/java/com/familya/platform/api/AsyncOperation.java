package com.familya.platform.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Envelope trả về cho mọi thao tác đột biến xuyên dịch vụ (theo ADR-007).
 *
 * <p>Gateway phát ra envelope này cùng mã trạng thái HTTP {@code 202 Accepted}.
 * Client sẽ tiếp tục gọi {@code GET /api/v2/operations/{operationId}} cho đến
 * khi trạng thái chuyển sang trạng thái kết thúc. Các trường {@code result},
 * {@code error} và {@code watermarks} là tùy chọn — chúng chỉ xuất hiện khi
 * thao tác đã có tiến triển tương ứng.</p>
 *
 * <p>Đây là {@code record} bất biến, đảm bảo dữ liệu envelope không bị thay đổi
 * sau khi được tạo, giúp an toàn khi truyền qua nhiều luồng.</p>
 *
 * @param operationId  định danh duy nhất của thao tác bất đồng bộ
 * @param status       trạng thái hiện tại của thao tác (xem {@link Status})
 * @param statusUrl    URL để client polling trạng thái (thường là
 *                     {@code /api/v2/operations/{operationId}})
 * @param result       kết quả nghiệp vụ (nếu có, khi {@code status} là {@code SUCCEEDED})
 * @param error        thông tin lỗi (nếu có, khi {@code status} là {@code FAILED})
 * @param watermarks   bản đồ watermark theo từng topic nguồn, phục vụ tái tạo/replay
 * @param updatedAt    thời điểm cập nhật envelope gần nhất
 *
 * @author Family Tree Platform Team
 */
public record AsyncOperation(
        UUID operationId,
        Status status,
        String statusUrl,
        Map<String, Object> result,
        ErrorBody error,
        Map<String, String> watermarks,
        Instant updatedAt
) {
    /**
     * Tập trạng thái khả dĩ của một thao tác bất đồng bộ.
     *
     * <p>Vòng đời điển hình:
     * {@code PENDING → RUNNING → SUCCEEDED} hoặc
     * {@code PENDING → RUNNING → COMPENSATING → COMPENSATED} hoặc
     * {@code PENDING → RUNNING → MANUAL_REVIEW}.</p>
     */
    public enum Status {
        /** Thao tác đã được chấp nhận nhưng chưa bắt đầu xử lý. */
        PENDING,
        /** Thao tác đang trong quá trình xử lý. */
        RUNNING,
        /** Thao tác đã hoàn tất thành công. */
        SUCCEEDED,
        /** Thao tác thất bại. */
        FAILED,
        /** Thao tác thất bại và đang được bù trừ (rollback/compensating action). */
        COMPENSATING,
        /** Thao tác đã được bù trừ thành công. */
        COMPENSATED,
        /** Thao tác cần can thiệp thủ công để giải quyết. */
        MANUAL_REVIEW
    }

    /**
     * Tạo envelope {@code PENDING} tiêu chuẩn cho một thao tác vừa được chấp nhận.
     *
     * <p>Các trường {@code result}, {@code error}, {@code watermarks} được để
     * {@code null}; {@code updatedAt} được gán bằng thời điểm hiện tại.</p>
     *
     * @param id        định danh thao tác vừa được cấp
     * @param statusUrl URL trạng thái để client polling
     * @return envelope {@link AsyncOperation} ở trạng thái {@link Status#PENDING}
     */
    public static AsyncOperation accepted(UUID id, String statusUrl) {
        // Khởi tạo envelope với trạng thái PENDING, không có kết quả/lỗi/watermark
        // và thời điểm cập nhật là hiện tại. Mọi trường tùy chọn đều null để
        // tuân thủ nguyên tắc "chỉ xuất hiện khi có dữ liệu".
        return new AsyncOperation(id, Status.PENDING, statusUrl, null, null, null, Instant.now());
    }

    /**
     * Cấu trúc con mô tả lỗi trả về trong envelope khi thao tác thất bại.
     *
     * @param code    mã lỗi ổn định theo máy (machine-stable)
     * @param message thông điệp lỗi dành cho người dùng (có thể bản địa hoá)
     * @param traceId mã truy vết OpenTelemetry, luôn có trong môi trường production
     * @param details bản đồ chi tiết lỗi (allowlisted, ví dụ: các field validation)
     */
    public record ErrorBody(String code, String message, String traceId, Map<String, Object> details) { }
}
