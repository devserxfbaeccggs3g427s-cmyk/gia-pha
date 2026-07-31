package com.familya.media.domain.exception;

/**
 * Ngoại lệ được ném ra khi bộ quét virus/an toàn không khả dụng.
 *
 * <p>Được sử dụng khi tài sản media cần được quét nhưng dịch vụ quét không thể
 * truy cập được (mất kết nối, dịch vụ ngừng hoạt động, quá thời gian chờ...).
 * Trong những trường hợp này, tài sản thường sẽ được chuyển sang trạng thái
 * cách ly hoặc thất bại tùy theo chính sách.
 */
public class ScannerUnavailableException extends RuntimeException {
    /**
     * Khởi tạo ngoại lệ với thông điệp mô tả.
     *
     * @param message thông điệp giải thích vì sao bộ quét không khả dụng
     */
    public ScannerUnavailableException(String message) { super(message); }
}
