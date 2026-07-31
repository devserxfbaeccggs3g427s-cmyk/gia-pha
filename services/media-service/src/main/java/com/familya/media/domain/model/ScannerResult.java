package com.familya.media.domain.model;

/**
 * Kết quả trả về từ bước quét virus/an toàn nội dung của một tài sản media.
 *
 * <p>Bản ghi này là kết quả thuần túy (pure value object) do bộ quét (scanner) trả về
 * sau khi kiểm tra một {@link MediaAsset}. Nó không chứa tham chiếu trực tiếp đến
 * tài sản media mà chỉ mang phán đoán an toàn và bằng chứng kèm theo (ví dụ: tên
 * chữ ký phát hiện, mã lỗi) để phục vụ truy vết và phản hồi cho người dùng.
 *
 * @param outcome  phán đoán của bộ quét (xem {@link Outcome})
 * @param evidence bằng chứng chi tiết đi kèm phán đoán, có thể null nếu không có
 */
public record ScannerResult(Outcome outcome, String evidence) {
    /**
     * Phán đoán của bộ quét đối với một tài sản media.
     */
    public enum Outcome {
        /** Tệp sạch, không phát hiện mối nguy. */
        CLEAN,
        /** Tệp bị phát hiện có nội dung độc hại (virus, malware, ...). */
        INFECTED,
        /** Quá trình quét thất bại (lỗi kỹ thuật, timeout, ...), chưa có kết luận. */
        FAILED
    }
}
