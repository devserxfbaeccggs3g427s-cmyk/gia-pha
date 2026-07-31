package com.familya.treeaccess.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.familya.platform.outbox.JdbcOutboxWriter;
import com.familya.platform.outbox.OutboxWriter;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class SagaReplyStager {

    private final OutboxWriter outbox;

    public SagaReplyStager(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    public void stageAck(UUID operationId, String participant, String stepCode,
                         long appliedVersion, long appliedEpoch, Instant now) {
        Map<String, Object> payload = basePayload(operationId, participant, stepCode, "ACK", now);
        payload.put("appliedAggregateVersion", appliedVersion);
        payload.put("appliedEpoch", appliedEpoch);
        stage(payload, participant);
    }

    public void stageFailed(JsonNode envelope, String participant, String stepCode,
                            String failureCode, String failureMessage) {
        Map<String, Object> payload = basePayload(
                UUID.fromString(envelope.path("operationId").asText()),
                participant, stepCode, "FAILED", Instant.now());
        payload.put("failureCode", failureCode);
        payload.put("failureMessage", failureMessage == null ? "" : failureMessage);
        payload.put("treeId", envelope.path("treeId").asText());
        stage(payload, participant);
    }

    private Map<String, Object> basePayload(UUID operationId, String participant, String stepCode,
                                            String status, Instant now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operationId", operationId.toString());
        payload.put("participantService", participant);
        payload.put("stepCode", stepCode);
        payload.put("status", status);
        payload.put("occurredAtEpochMs", now.toEpochMilli());
        payload.put("schemaVersion", "v1");
        return payload;
    }

    private void stage(Map<String, Object> payload, String participant) {
        String topic = participant + ".replies.v1";
        String operationId = (String) payload.get("operationId");
        JdbcOutboxWriter.Builder b = JdbcOutboxWriter.builder()
                .create("saga-reply", operationId, 1L,
                        "SagaParticipantReply", 1, topic, operationId, payload);
        b.header("eventType", "SagaParticipantReply");
        b.header("operationId", operationId);
        outbox.stage(b.build());
    }
}