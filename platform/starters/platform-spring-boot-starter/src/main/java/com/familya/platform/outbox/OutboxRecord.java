package com.familya.platform.outbox;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Bản ghi outbox transactional.
 *
 * <p>Producer commit trạng thái nghiệp vụ và các hàng outbox trong cùng một
 * transaction cục bộ; một relay riêng (xem {@link OutboxRelay}) sẽ đọc outbox
 * và publish lên Kafka kèm theo các header metadata bắt buộc.</p>
 *
 * <p>Mẫu thiết kế này (transactional outbox) đảm bảo:</p>
 * <ul>
 *   <li>Không bao giờ xảy ra tình trạng "đã thay đổi DB nhưng chưa phát sự kiện".</li>
 *   <li>Không bao giờ xảy ra tình trạng "đã phát sự kiện nhưng thay đổi DB bị rollback".</li>
 *   <li>Cho phép tách biệt tốc độ giữa xử lý nghiệp vụ và publish message.</li>
 * </ul>
 *
 * <p>Đây là {@code record} bất biến với constructor compact tự bảo đảm
 * {@code headers} không null (mặc định là {@link Map#of()}).</p>
 *
 * @param id                định danh duy nhất của bản ghi (UUID)
 * @param aggregateType     loại aggregate (vd {@code Tree}, {@code Member})
 * @param aggregateId       định danh aggregate
 * @param aggregateVersion  phiên bản aggregate (dùng cho kiểm tra tương tranh)
 * @param eventType         loại sự kiện (vd {@code TreeCreated.v1})
 * @param eventVersion      phiên bản schema của sự kiện
 * @param topic             topic Kafka đích
 * @param partitionKey      khoá phân vùng (thường là {@code aggregate_id})
 * @param correlationId     id tương quan xuyên suốt workflow
 * @param causationId       id nguyên nhân trực tiếp
 * @param operationId       id thao tác bất đồng bộ
 * @param traceparent       W3C trace context
 * @param payloadJson       payload đã được serialize thành JSON
 * @param headers           bản đồ header tuỳ ý
 * @param occurredAt        thời điểm phát sinh sự kiện
 * @param lockedUntil       thời điểm khoá relay hết hạn (null nếu chưa bị khoá)
 *
 * @author Family Tree Platform Team
 */
public record OutboxRecord(
        UUID id,
        String aggregateType,
        String aggregateId,
        long aggregateVersion,
        String eventType,
        int eventVersion,
        String topic,
        String partitionKey,
        String correlationId,
        String causationId,
        String operationId,
        String traceparent,
        String payloadJson,
        Map<String, String> headers,
        Instant occurredAt,
        Instant lockedUntil
) {
    /**
     * Constructor compact — đảm bảo {@code headers} không bao giờ là null,
     * mặc định hoá thành {@link Map#of()} để các consumer downstream có thể
     * gọi {@link Map#get} một cách an toàn.
     *
     * @param headers bản đồ header truyền vào (có thể null)
     */
    public OutboxRecord {
        if (headers == null) {
            headers = Map.of();
        }
    }
}
