package com.familya.media.application.port.out;

import com.familya.platform.outbox.OutboxRecord;

import java.util.List;

/**
 * Port ra (driven port) của kiến trúc hexagonal: bảng outbox dùng cho
 * transactional outbox pattern.
 *
 * <p>Mục đích: các sự kiện cần phát ra ngoài (Kafka, webhook, ...) được
 * ghi vào outbox trong cùng transaction với mutation DB, sau đó một
 * relay worker đọc {@link #listPending(int)} và đẩy đi. Nhờ vậy đảm
 * bảo tính nhất quán: không có mutation mà thiếu sự kiện tương ứng.</p>
 */
public interface MediaOutbox {

    /**
     * Ghi một outbox record trong transaction hiện tại.
     *
     * @param record bản ghi outbox; phải có {@code id} duy nhất và
     *               payload đã serialize.
     */
    void stage(OutboxRecord record);

    /**
     * Lấy danh sách các outbox record đang chờ xử lý.
     *
     * @param limit số bản ghi tối đa trả về; relay worker dùng để chunk
     *              xử lý.
     * @return danh sách record ở trạng thái pending; rỗng nếu không có.
     */
    List<OutboxRecord> listPending(int limit);
}
