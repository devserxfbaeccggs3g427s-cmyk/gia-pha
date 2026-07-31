package com.familya.treeaccess.adapter.out.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.familya.platform.outbox.JdbcOutboxWriter;
import com.familya.platform.outbox.OutboxWriter;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

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
        b.header("ownerService", String.valueOf(payload.getOrDefault("ownerService", "tree-access-service")));
        outbox.stage(b.build());
    }
}