package com.familya.identity.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Lớp cơ sở trừu tượng cho tất cả các sự kiện domain trong ngữ cảnh
 * "identity".
 *
 * <p>Mỗi sự kiện domain cần cung cấp:
 * <ul>
 *     <li>{@link #userId()} – người dùng liên quan.</li>
 *     <li>{@link #eventType()} – tên loại sự kiện (thường khớp với tên lớp).</li>
 *     <li>{@link #eventVersion()} – phiên bản schema của sự kiện, hỗ trợ
 *         versioning ở phía consumer.</li>
 *     <li>{@link #occurredAt()} – thời điểm xảy ra.</li>
 * </ul>
 *
 * <p>Sự kiện domain được phát hành qua
 * {@link com.familya.identity.application.port.out.IdentityEventPublisher}
 * và dùng cho:
 * <ul>
 *     <li>Đồng bộ dữ liệu với các service khác (qua Outbox + Kafka).</li>
 *     <li>Audit, analytics.</li>
 *     <li>Kích hoạt các hành động phụ (gửi email chào mừng, thông báo,…).</li>
 * </ul>
 *
 * @author Familya Platform Team
 * @since 1.0.0
 */
public abstract class IdentityEvent {
    /**
     * UUID của người dùng liên quan đến sự kiện.
     *
     * @return UUID người dùng.
     */
    public abstract UUID userId();

    /**
     * Tên loại sự kiện, ví dụ: {@code "IdentityUserCreated"}.
     * Thường dùng cho việc định tuyến ở phía consumer.
     *
     * @return chuỗi không rỗng, phân biệt hoa thường.
     */
    public abstract String eventType();

    /**
     * Phiên bản schema của sự kiện.
     *
     * <p>Phiên bản được sử dụng để hỗ trợ tương thích ngược khi payload
     * sự kiện thay đổi. Consumer nên đọc giá trị này để áp dụng bộ
     * giải mã/phiên dịch phù hợp.
     *
     * @return số phiên bản (bắt đầu từ 1).
     */
    public abstract int eventVersion();

    /**
     * Thời điểm sự kiện xảy ra.
     *
     * @return {@link Instant} đại diện cho thời điểm phát sinh.
     */
    public abstract Instant occurredAt();
}
