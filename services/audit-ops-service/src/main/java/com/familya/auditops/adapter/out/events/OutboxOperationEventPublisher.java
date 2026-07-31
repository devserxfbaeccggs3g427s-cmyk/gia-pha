/**
 * Adapter cho {@link com.familya.auditops.application.port.out.OperationEventBus}
 * sử dụng outbox của platform để publish {@link com.familya.auditops.domain.event.AuditOpsEvent}.
 *
 * <p>Mỗi event sẽ trở thành một outbox row trong transaction hiện tại;
 * relay tiêu chuẩn ({@code platform-outbox-starter}) sẽ publish lên
 * Kafka cùng với các header bắt buộc theo chuẩn.</p>
 */
package com.familya.auditops.adapter.out.events;

import com.familya.auditops.application.port.out.OperationEventBus;
import com.familya.auditops.domain.event.AuditOpsEvent;
import com.familya.platform.outbox.JdbcOutboxWriter;
import com.familya.platform.outbox.OutboxWriter;
import com.familya.platform.telemetry.PlatformMetrics;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Triển khai {@link OperationEventBus} bằng cách stage outbox row.
 *
 * <p>Lớp này đảm bảo publish "at-least-once" đúng cách: nhờ outbox,
 * event chỉ được gửi tới Kafka khi transaction với database thành công.</p>
 */
@Component
public class OutboxOperationEventPublisher implements OperationEventBus {

    /** Outbox writer của platform. */
    private final OutboxWriter outbox;
    /** Metric collector. */
    private final PlatformMetrics metrics;

    /**
     * Khởi tạo publisher.
     *
     * @param outbox  outbox writer
     * @param metrics metric collector
     */
    public OutboxOperationEventPublisher(OutboxWriter outbox, PlatformMetrics metrics) {
        this.outbox = outbox;
        this.metrics = metrics;
    }

    /**
     * Stage một {@link AuditOpsEvent} vào outbox.
     *
     * <p>Các bước:</p>
     * <ol>
     *   <li>Chuyển các trường của event sang {@code Map} để serialize.</li>
     *   <li>Tạo {@code JdbcOutboxWriter.Builder} với aggregate type
     *       {@code "operation"} và partition key là operationId.</li>
     *   <li>Thêm các header chuẩn (eventType, eventVersion, operationId).</li>
     *   <li>Thêm các header bổ sung do caller cung cấp.</li>
     *   <li>Stage row vào outbox và ghi metric.</li>
     * </ol>
     *
     * @param event        sự kiện cần publish
     * @param extraHeaders header bổ sung (có thể null)
     */
    @Override
    public void publish(AuditOpsEvent event, Map<String, String> extraHeaders) {
        // Chuẩn bị payload cho outbox row.
        Map<String, Object> payload = new HashMap<>();
        payload.put("operationId", event.operationId().toString());
        payload.put("eventType", event.eventType());
        payload.put("eventVersion", event.eventVersion());
        payload.put("occurredAt", event.occurredAt().toString());
        // Dựng builder cho outbox. aggregateType là "operation", partition key là operationId
        // để đảm bảo thứ tự sự kiện theo từng operation.
        JdbcOutboxWriter.Builder b = JdbcOutboxWriter.builder()
                .create("operation", event.operationId().toString(), 1L,
                        event.eventType(), event.eventVersion(),
                        event.topic(), event.partitionKey(), payload);
        // Header chuẩn theo convention của platform.
        b.header("eventType", event.eventType());
        b.header("eventVersion", String.valueOf(event.eventVersion()));
        b.header("operationId", event.operationId().toString());
        // Gộp thêm các header do caller cung cấp (correlation, causation, ...).
        if (extraHeaders != null) {
            extraHeaders.forEach(b::header);
        }
        // Stage row vào outbox; relay sẽ publish sau khi transaction commit.
        outbox.stage(b.build());
        metrics.outboxStaged("audit-ops-service", event.eventType());
    }
}