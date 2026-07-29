package com.familya.member.adapter.out.events;

import com.familya.platform.outbox.JdbcOutboxWriter;
import com.familya.platform.outbox.OutboxWriter;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Stages OperationStarted / OperationStateChanged lifecycle events on the
 * local outbox. Owned by every Saga owner service (Member, Tree Access, ...)
 * and consumed by Audit Ops as a projection. See catalog.yaml.
 */
@Component
public class OperationLifecycleOutboxStager {

    private final OutboxWriter outbox;

    public OperationLifecycleOutboxStager(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    public void stage(Map<String, Object> payload, String topic, String eventType) {
        String operationId = String.valueOf(payload.get("operationId"));
        JdbcOutboxWriter.Builder b = JdbcOutboxWriter.builder()
                .create("operation", operationId, 1L,
                        eventType, 1, topic, operationId, payload);
        b.header("eventType", eventType);
        b.header("operationId", operationId);
        b.header("ownerService", String.valueOf(payload.getOrDefault("ownerService", "member-service")));
        outbox.stage(b.build());
    }
}