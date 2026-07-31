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
 * Outbox adapter for {@link AuditOpsEvent}. Each event becomes an
 * outbox row in the current transaction; the standard relay
 * ({@code platform-outbox-starter}) publishes to Kafka with the
 * required metadata headers.
 */
@Component
public class OutboxOperationEventPublisher implements OperationEventBus {

    private final OutboxWriter outbox;
    private final PlatformMetrics metrics;

    public OutboxOperationEventPublisher(OutboxWriter outbox, PlatformMetrics metrics) {
        this.outbox = outbox;
        this.metrics = metrics;
    }

    @Override
    public void publish(AuditOpsEvent event, Map<String, String> extraHeaders) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("operationId", event.operationId().toString());
        payload.put("eventType", event.eventType());
        payload.put("eventVersion", event.eventVersion());
        payload.put("occurredAt", event.occurredAt().toString());
        JdbcOutboxWriter.Builder b = JdbcOutboxWriter.builder()
                .create("operation", event.operationId().toString(), 1L,
                        event.eventType(), event.eventVersion(),
                        event.topic(), event.partitionKey(), payload);
        b.header("eventType", event.eventType());
        b.header("eventVersion", String.valueOf(event.eventVersion()));
        b.header("operationId", event.operationId().toString());
        if (extraHeaders != null) {
            extraHeaders.forEach(b::header);
        }
        outbox.stage(b.build());
        metrics.outboxStaged("audit-ops-service", event.eventType());
    }
}