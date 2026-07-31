package com.familya.media.domain.exception;

/**
 * Ngoại lệ được ném ra khi một tham chiếu (reference) không khả dụng.
 *
 * <p>Được sử dụng khi tài sản media hoặc đối tượng liên quan tham chiếu đến một
 * tài nguyên (ví dụ: tệp trong bộ lưu trữ, bản ghi khác) nhưng tài nguyên đó
 * tạm thời không thể truy cập được do lỗi mạng, lỗi lưu trữ hoặc đang bảo trì.
 */
public class ReferenceUnavailableException extends RuntimeException {
    /**
     * Khởi tạo ngoại lệ với thông điệp mô tả.
     *
     * @param message thông điệp giải thích tham chiếu nào đang không khả dụng
     */
    public ReferenceUnavailableException(String message) { super(message); }
}
