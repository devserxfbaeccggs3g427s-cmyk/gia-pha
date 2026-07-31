package com.familya.relationship.adapter.out.events;

import com.familya.platform.outbox.JdbcOutboxWriter;
import com.familya.platform.outbox.OutboxWriter;
import com.familya.platform.telemetry.PlatformMetrics;
import com.familya.relationship.application.port.out.RelationshipEventPublisher;
import com.familya.relationship.domain.event.RelationshipEvent;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adapter triển khai cổng {@link RelationshipEventPublisher} bằng mẫu
 * <b>transactional outbox</b>.
 * <p>
 * Thay vì gọi thẳng Kafka, sự kiện được ghi vào bảng outbox trong cùng
 * transaction với lệnh ghi dữ liệu nghiệp vụ. Một worker của platform sẽ
 * đọc outbox và đẩy lên Kafka. Cách tiếp cận này đảm bảo:
 * </p>
 * <ul>
 *   <li>Sự kiện được phát hành đồng bộ với dữ liệu nghiệp vụ (atomicity).</li>
 *   <li>Hệ thống không mất sự kiện khi Kafka tạm thời không khả dụng.</li>
 *   <li>Có thể replay lại sự kiện khi cần tái dựng projection.</li>
 * </ul>
 *
 * <h2>Cấu trúc payload</h2>
 * <p>Mỗi sự kiện được tuần tự hóa thành JSON với các trường:</p>
 * <ul>
 *   <li>{@code treeId}, {@code eventType}, {@code eventVersion}, {@code occurredAt},
 *       {@code commandSeq}.</li>
 * </ul>
 * <p>Cùng với các header Kafka: {@code eventType}, {@code eventVersion},
 * {@code treeId}, {@code commandSeq} - giúp consumer dễ dàng lọc và routing.</p>
 */
@Component
public class OutboxRelationshipEventPublisher implements RelationshipEventPublisher {

    /** Cổng ghi outbox do platform cung cấp. */
    private final OutboxWriter outbox;
    /** Bộ đếm metric. */
    private final PlatformMetrics metrics;

    /**
     * Khởi tạo adapter.
     *
     * @param outbox  cổng ghi outbox
     * @param metrics bộ đếm metric
     */
    public OutboxRelationshipEventPublisher(OutboxWriter outbox, PlatformMetrics metrics) {
        this.outbox = outbox;
        this.metrics = metrics;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Quy trình:
     * </p>
     * <ol>
     *   <li>Xây dựng payload JSON từ các trường của sự kiện.</li>
     *   <li>Tạo {@link JdbcOutboxWriter.Builder} với topic và partition key mặc
     *       định của sự kiện.</li>
     *   <li>Gắn các header (eventType, eventVersion, treeId, commandSeq) để
     *       consumer có thể routing/lọc theo header mà không cần parse payload.</li>
     *   <li>Stage outbox và ghi nhận metric.</li>
     * </ol>
     *
     * @param event sự kiện cần phát hành
     */
    @Override
    public void publish(RelationshipEvent event) {
        // Bước 1: xây dựng payload JSON. Dùng LinkedHashMap để giữ thứ tự trường.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("treeId", event.treeId().toString());
        payload.put("eventType", event.eventType());
        payload.put("eventVersion", event.eventVersion());
        payload.put("occurredAt", event.occurredAt().toString());
        payload.put("commandSeq", event.commandSeq());
        // Bước 2: cấu hình builder với aggregate="relationship", partition theo treeId.
        JdbcOutboxWriter.Builder b = JdbcOutboxWriter.builder()
                .create("relationship", event.treeId().toString(), event.commandSeq(),
                        event.eventType(), event.eventVersion(),
                        event.topic(), event.partitionKey(), payload);
        // Bước 3: gắn header cho Kafka - giúp consumer lọc và routing.
        b.header("eventType", event.eventType());
        b.header("eventVersion", String.valueOf(event.eventVersion()));
        b.header("treeId", event.treeId().toString());
        b.header("commandSeq", String.valueOf(event.commandSeq()));
        // Bước 4: ghi vào outbox trong transaction hiện tại.
        outbox.stage(b.build());
        // Ghi nhận metric cho observability.
        metrics.outboxStaged("relationship-service", event.eventType());
    }
}