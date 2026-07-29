package com.familya.identity.adapter.out.events;

import com.familya.identity.application.port.out.IdentityEventPublisher;
import com.familya.identity.domain.event.IdentityEvent;
import com.familya.platform.outbox.JdbcOutboxWriter;
import com.familya.platform.outbox.OutboxWriter;
import com.familya.platform.telemetry.PlatformMetrics;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OutboxIdentityEventPublisher implements IdentityEventPublisher {

    private final OutboxWriter outbox;
    private final PlatformMetrics metrics;

    public OutboxIdentityEventPublisher(OutboxWriter outbox, PlatformMetrics metrics) {
        this.outbox = outbox;
        this.metrics = metrics;
    }

    @Override
    public void publish(IdentityEvent event) {
        Map<String, Object> payload = Map.of(
                "userId", event.userId().toString(),
                "eventType", event.eventType(),
                "eventVersion", event.eventVersion(),
                "occurredAt", event.occurredAt().toString()
        );
        JdbcOutboxWriter.Builder b = JdbcOutboxWriter.builder()
                .create("user", event.userId().toString(), 1L,
                        event.eventType(), event.eventVersion(),
                        "identity.events.v1", event.userId().toString(), payload);
        b.header("eventType", event.eventType());
        b.header("eventVersion", String.valueOf(event.eventVersion()));
        outbox.stage(b.build());
        metrics.outboxStaged("identity-service", event.eventType());
    }
}
