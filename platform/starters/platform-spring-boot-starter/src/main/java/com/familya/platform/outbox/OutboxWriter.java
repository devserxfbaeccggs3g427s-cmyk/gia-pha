package com.familya.platform.outbox;

/**
 * Cổng (port) ghi vào bảng outbox cục bộ.
 *
 * <p>Triển khai được ràng buộc với transaction hiện tại và BẮT BUỘC phải được
 * gọi bên trong một {@code @Transactional} cũng đang ghi aggregate nghiệp vụ.
 * Điều này đảm bảo tính nguyên tử: nếu transaction commit, cả thay đổi nghiệp
 * vụ lẫn bản ghi outbox đều được lưu; nếu rollback, cả hai đều bị huỷ.</p>
 *
 * <p>Relay (xem {@link OutboxRelay}) sẽ đọc các bản ghi đã commit và publish
 * chúng tới Kafka — tách biệt hoàn toàn khỏi transaction nghiệp vụ.</p>
 *
 * @author Family Tree Platform Team
 */
public interface OutboxWriter {

    /**
     * Stage (ghi tạm) một bản ghi outbox trong transaction hiện tại.
     *
     * <p>Bản ghi sẽ được lưu vào bảng {@code outbox_record} và chỉ được relay
     * publish sau khi transaction commit thành công.</p>
     *
     * @param record bản ghi outbox cần ghi tạm
     */
    void stage(OutboxRecord record);
}
