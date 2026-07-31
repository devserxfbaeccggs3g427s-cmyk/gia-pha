package com.familya.event.adapter.out.events;

import com.familya.event.application.port.out.EventChangePublisher;
import com.familya.event.domain.event.DomainEventChange;
import com.familya.platform.outbox.JdbcOutboxWriter;
import com.familya.platform.outbox.OutboxWriter;
import com.familya.platform.telemetry.PlatformMetrics;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Triển khai {@link EventChangePublisher} dựa trên <b>transactional outbox
 * pattern</b>: sự kiện thay đổi miền ({@link DomainEventChange}) được
 * ghi vào bảng outbox trong cùng giao dịch DB với mutation aggregate.
 *
 * <p>Relay sẽ đọc bảng outbox theo chu kỳ (xem {@code application.yml}:
 * {@code familya.outbox.relay.interval-ms}) và publish lên Kafka. Cách
 * làm này đảm bảo:
 * <ul>
 *   <li>Không mất sự kiện khi Kafka tạm thời không khả dụng.</li>
 *   <li>Tính atomic giữa DB và Kafka — không cần 2PC.</li>
 *   <li>Có thể replay từ bảng outbox khi cần.</li>
 * </ul>
 *
 * @author gia-pha platform
 */
@Component
public class OutboxEventChangePublisher implements EventChangePublisher {

    private final OutboxWriter outbox;
    private final PlatformMetrics metrics;

    /**
     * Khởi tạo publisher.
     *
     * @param outbox  writer được tiêm từ platform.
     * @param metrics chỉ số quan sát.
     */
    public OutboxEventChangePublisher(OutboxWriter outbox, PlatformMetrics metrics) {
        this.outbox = outbox;
        this.metrics = metrics;
    }

    /**
     * Ghi một sự kiện {@link DomainEventChange} vào outbox.
     *
     * <p>Quy trình:
     * <ol>
     *   <li>Serialize các trường chính của sự kiện vào một map.</li>
     *   <li>Tạo {@link JdbcOutboxWriter.Builder} với aggregate type
     *       {@code "event"}, partition key là {@code treeId}, topic là
     *       {@link DomainEventChange#topic()}.</li>
     *   <li>Gắn các header chuẩn (eventType, eventVersion, treeId, eventId)
     *       để consumer dễ trích xuất mà không cần parse payload.</li>
     *   <li>Gọi {@link OutboxWriter#stage} để ghi bản ghi vào bảng outbox.</li>
     *   <li>Ghi nhận metric {@code outbox.staged}.</li>
     * </ol>
     *
     * @param change sự kiện thay đổi cần phát hành.
     */
    @Override
    public void publish(DomainEventChange change) {
        // Bước 1: payload chứa các trường "thường trực" của event — giúp consumer
        // deserialize thẳng mà không cần biết schema của aggregate.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("treeId", change.treeId().toString());
        payload.put("eventId", change.eventId().toString());
        payload.put("eventType", change.eventType());
        payload.put("eventVersion", change.eventVersion());
        payload.put("revision", change.revision());
        payload.put("occurredAt", change.occurredAt().toString());

        // Bước 2: tạo builder với đầy đủ thông tin routing.
        JdbcOutboxWriter.Builder b = JdbcOutboxWriter.builder()
                .create("event", change.eventId().toString(), change.revision(),
                        change.eventType(), change.eventVersion(),
                        change.topic(), change.partitionKey(), payload);

        // Bước 3: gắn header — dùng cho filter/route phía consumer.
        b.header("eventType", change.eventType());
        b.header("eventVersion", String.valueOf(change.eventVersion()));
        b.header("treeId", change.treeId().toString());
        b.header("eventId", change.eventId().toString());

        // Bước 4: stage vào outbox (cùng transaction với DB mutation).
        outbox.stage(b.build());

        // Bước 5: metric đầu ra.
        metrics.outboxStaged("event-service", change.eventType());
    }
}
