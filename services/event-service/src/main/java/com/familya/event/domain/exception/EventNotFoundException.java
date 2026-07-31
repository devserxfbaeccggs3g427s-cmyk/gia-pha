package com.familya.event.domain.exception;

/**
 * Ngoại lệ nghiệp vụ được ném khi không tìm thấy
 * {@link com.familya.event.domain.model.DomainEvent sự kiện gia phả}
 * theo {@code eventId} yêu cầu.
 *
 * <h2>Ngữ cảnh phát sinh</h2>
 * <p>Được ném từ các use case thao tác trực tiếp lên aggregate, ví dụ:
 * <ul>
 *   <li>{@link com.familya.event.application.usecase.UpdateDomainEventUseCase}</li>
 *   <li>{@link com.familya.event.application.usecase.TombstoneDomainEventUseCase}</li>
 * </ul>
 *
 * <p>Tại REST layer, ngoại lệ này thường được ánh xạ sang HTTP
 * {@code 404 Not Found} bởi advice xử lý lỗi toàn cục của platform.
 *
 * @author gia-pha platform
 */
public class EventNotFoundException extends RuntimeException {

    /**
     * Khởi tạo ngoại lệ với thông điệp chi tiết (thường kèm
     * {@code eventId} để dễ truy vết).
     *
     * @param message mô tả lý do không tìm thấy.
     */
    public EventNotFoundException(String message) { super(message); }
}
