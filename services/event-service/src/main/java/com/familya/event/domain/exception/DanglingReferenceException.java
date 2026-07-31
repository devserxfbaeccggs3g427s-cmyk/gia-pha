package com.familya.event.domain.exception;

/**
 * Ngoại lệ nghiệp vụ biểu thị rằng lệnh cập nhật trỏ tới một (hoặc
 * nhiều) tham chiếu <b>đứt (dangling)</b> — tức là ID thành viên hoặc
 * media không tồn tại trong projection tham chiếu cục bộ.
 *
 * <h2>Ngữ cảnh phát sinh</h2>
 * <p>Ngoại lệ này được ném từ
 * {@link com.familya.event.application.usecase.CreateDomainEventUseCase}
 * và
 * {@link com.familya.event.application.usecase.UpdateDomainEventUseCase}
 * khi phát hiện tham chiếu không hợp lệ. Việc ném ngoại lệ (thay vì
 * chấp nhận âm thầm) đảm bảo <b>tính toàn vẹn tham chiếu</b> giữa
 * event-service và các dịch vụ khác.
 *
 * <h2>Quy ước truyền thông</h2>
 * <p>Thông điệp lỗi nên bao gồm danh sách ID vi phạm để client có thể
 * hiển thị cho người dùng hoặc phục vụ retry với projection đã cập nhật.
 *
 * @author gia-pha platform
 */
public class DanglingReferenceException extends RuntimeException {

    /**
     * Khởi tạo ngoại lệ với thông điệp chi tiết.
     *
     * @param message mô tả lý do vi phạm tham chiếu (nên liệt kê các ID).
     */
    public DanglingReferenceException(String message) { super(message); }
}
