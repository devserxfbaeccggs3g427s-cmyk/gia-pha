package com.familya.member.adapter.out.events;

import com.familya.member.application.port.out.MemberEventPublisher;
import com.familya.member.domain.event.MemberEvent;
import com.familya.platform.outbox.JdbcOutboxWriter;
import com.familya.platform.outbox.OutboxWriter;
import com.familya.platform.telemetry.PlatformMetrics;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OutboxMemberEventPublisher implements MemberEventPublisher {

    private final OutboxWriter outbox;
    private final PlatformMetrics metrics;

    public OutboxMemberEventPublisher(OutboxWriter outbox, PlatformMetrics metrics) {
        this.outbox = outbox;
        this.metrics = metrics;
    }

    @Override
    public void publish(MemberEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("treeId", event.treeId().toString());
        payload.put("memberId", event.memberId().toString());
        payload.put("eventType", event.eventType());
        payload.put("eventVersion", event.eventVersion());
        payload.put("occurredAt", event.occurredAt().toString());
        JdbcOutboxWriter.Builder b = JdbcOutboxWriter.builder()
                .create("member", event.memberId().toString(), 1L,
                        event.eventType(), event.eventVersion(),
                        event.topic(), event.partitionKey(), payload);
        b.header("eventType", event.eventType());
        b.header("eventVersion", String.valueOf(event.eventVersion()));
        b.header("treeId", event.treeId().toString());
        b.header("memberId", event.memberId().toString());
        outbox.stage(b.build());
        metrics.outboxStaged("member-service", event.eventType());
    }
}