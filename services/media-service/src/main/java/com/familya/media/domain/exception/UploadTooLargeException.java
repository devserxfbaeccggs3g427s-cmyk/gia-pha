package com.familya.media.domain.exception;

/**
 * Ngoại lệ được ném ra khi tệp tải lên vượt quá giới hạn kích thước cho phép.
 *
 * <p>Được sử dụng để bảo vệ hệ thống khỏi các tệp quá lớn, đảm bảo tài nguyên
 * lưu trữ và băng thông được sử dụng hợp lý. Giới hạn cụ thể do cấu hình
 * của dịch vụ media quy định.
 */
public class UploadTooLargeException extends RuntimeException {
    /**
     * Khởi tạo ngoại lệ với thông điệp mô tả.
     *
     * @param message thông điệp giải thích kích thước tải lên và giới hạn cho phép
     */
    public UploadTooLargeException(String message) { super(message); }
}
