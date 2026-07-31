package com.familya.event.adapter.out.events;

import com.familya.event.application.port.out.EventChangePublisher;
import com.familya.event.domain.event.DomainEventChange;
import com.familya.platform.outbox.JdbcOutboxWriter;
import com.familya.platform.outbox.OutboxWriter;
import com.familya.platform.telemetry.PlatformMetrics;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OutboxEventChangePublisher implements EventChangePublisher {

    private final OutboxWriter outbox;
    private final PlatformMetrics metrics;

    public OutboxEventChangePublisher(OutboxWriter outbox, PlatformMetrics metrics) {
        this.outbox = outbox;
        this.metrics = metrics;
    }

    @Override
    public void publish(DomainEventChange change) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("treeId", change.treeId().toString());
        payload.put("eventId", change.eventId().toString());
        payload.put("eventType", change.eventType());
        payload.put("eventVersion", change.eventVersion());
        payload.put("revision", change.revision());
        payload.put("occurredAt", change.occurredAt().toString());
        JdbcOutboxWriter.Builder b = JdbcOutboxWriter.builder()
                .create("event", change.eventId().toString(), change.revision(),
                        change.eventType(), change.eventVersion(),
                        change.topic(), change.partitionKey(), payload);
        b.header("eventType", change.eventType());
        b.header("eventVersion", String.valueOf(change.eventVersion()));
        b.header("treeId", change.treeId().toString());
        b.header("eventId", change.eventId().toString());
        outbox.stage(b.build());
        metrics.outboxStaged("event-service", change.eventType());
    }
}