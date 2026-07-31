package com.familya.identity.application.port.out;

import com.familya.identity.domain.event.IdentityEvent;

/**
 * Port (cổng) xuất sự kiện identity ra bên ngoài.
 *
 * <p>Đây là một "port out" trong kiến trúc hexagonal – use case chỉ
 * phụ thuộc vào interface này mà không quan tâm tới cách thức phát
 * hành sự kiện (Kafka, RabbitMQ, Outbox, in-memory,…). Nhờ vậy:
 * <ul>
 *     <li>Code use case dễ test với mock.</li>
 *     <li>Có thể thay đổi chiến lược phát hành (ví dụ: chuyển từ
 *         outbox sang pub/sub) mà không sửa use case.</li>
 * </ul>
 *
 * <p>Triển khai mặc định trong service này là
 * {@link com.familya.identity.adapter.out.events.OutboxIdentityEventPublisher}
 * – sử dụng Outbox Pattern.
 *
 * @author Familya Platform Team
 * @since 1.0.0
 */
public interface IdentityEventPublisher {
    /**
     * Phát hành một sự kiện domain.
     *
     * <p>Triển khai có thể:
     * <ul>
     *     <li>Ghi vào bảng outbox trong cùng transaction cơ sở dữ liệu.</li>
     *     <li>Đẩy trực tiếp lên message broker.</li>
     *     <li>Ghi vào hàng đợi in-memory cho test.</li>
     * </ul>
     *
     * @param event sự kiện domain cần phát hành.
     */
    void publish(IdentityEvent event);
}
