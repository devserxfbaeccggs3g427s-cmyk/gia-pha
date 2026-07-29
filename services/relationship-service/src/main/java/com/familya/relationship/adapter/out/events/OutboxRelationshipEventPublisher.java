package com.familya.relationship.adapter.out.events;

import com.familya.platform.outbox.JdbcOutboxWriter;
import com.familya.platform.outbox.OutboxWriter;
import com.familya.platform.telemetry.PlatformMetrics;
import com.familya.relationship.application.port.out.RelationshipEventPublisher;
import com.familya.relationship.domain.event.RelationshipEvent;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OutboxRelationshipEventPublisher implements RelationshipEventPublisher {

    private final OutboxWriter outbox;
    private final PlatformMetrics metrics;

    public OutboxRelationshipEventPublisher(OutboxWriter outbox, PlatformMetrics metrics) {
        this.outbox = outbox;
        this.metrics = metrics;
    }

    @Override
    public void publish(RelationshipEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("treeId", event.treeId().toString());
        payload.put("eventType", event.eventType());
        payload.put("eventVersion", event.eventVersion());
        payload.put("occurredAt", event.occurredAt().toString());
        payload.put("commandSeq", event.commandSeq());
        JdbcOutboxWriter.Builder b = JdbcOutboxWriter.builder()
                .create("relationship", event.treeId().toString(), event.commandSeq(),
                        event.eventType(), event.eventVersion(),
                        event.topic(), event.partitionKey(), payload);
        b.header("eventType", event.eventType());
        b.header("eventVersion", String.valueOf(event.eventVersion()));
        b.header("treeId", event.treeId().toString());
        b.header("commandSeq", String.valueOf(event.commandSeq()));
        outbox.stage(b.build());
        metrics.outboxStaged("relationship-service", event.eventType());
    }
}