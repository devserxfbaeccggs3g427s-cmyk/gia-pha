package com.familya.media.application.port.out;

import com.familya.media.domain.event.MediaChange;

/**
 * Port ra (driven port) của kiến trúc hexagonal: xuất bản sự kiện thay
 * đổi media tới hạ tầng messaging (Kafka, outbox, ...).
 *
 * <p>Mọi use case ghi cần phát {@link MediaChange} để downstream
 * (search index, projection, sync sang service khác) consume. Triển
 * khai đảm bảo:</p>
 * <ul>
 *   <li>Idempotent producer: cùng {@code changeId} không được phát hai
 *       lần.</li>
 *   <li>Giao nhận theo cơ chế outbox/inbox của platform.</li>
 * </ul>
 */
public interface MediaChangePublisher {

    /**
     * Phát một sự kiện thay đổi media.
     *
     * <p>Phương thức này thường được gọi trong cùng transaction với
     * ghi DB; triển khai có thể ghi vào outbox table và để relay
     * worker đẩy lên broker.</p>
     *
     * @param change sự kiện thay đổi; {@code changeId} phải duy nhất
     *               trong phạm vi partition.
     */
    void publish(MediaChange change);
}
