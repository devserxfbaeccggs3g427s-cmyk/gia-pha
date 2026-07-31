package com.familya.search.domain.exception;

/**
 * Ngoại lệ được ném khi watermark hiện tại nhỏ hơn watermark mà client yêu
 * cầu, tức là barrier (rào chắn) phiên bản chưa đạt đến mức kỳ vọng.
 *
 * <p>Trong bối cảnh CQRS/projection, đây là tín hiệu để client biết rằng các
 * miền dữ liệu liên quan chưa hội tụ, việc đọc có thể trả về kết quả cũ.
 * Client có thể quyết định thử lại hoặc chấp nhận kết quả hiện tại.</p>
 *
 * <p>Các use case {@code ComputeStatisticsUseCase} và {@code ComputeReportUseCase}
 * sử dụng ngoại lệ này khi {@code requestedWatermark} vượt quá khả năng đáp
 * ứng của projection.</p>
 */
public class StaleBarrierException extends RuntimeException {
    /**
     * Khởi tạo ngoại lệ với thông điệp mô tả.
     *
     * @param message thông điệp mô tả lý do bị stale, thường ghi rõ giá trị
     *                watermark hiện tại và watermark được yêu cầu.
     */
    public StaleBarrierException(String message) { super(message); }
}
