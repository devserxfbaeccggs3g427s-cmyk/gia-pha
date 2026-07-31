package com.familya.media.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.familya.platform.outbox.JdbcOutboxWriter;
import com.familya.platform.outbox.OutboxWriter;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Helper stage các bản tin phản hồi saga ({@code SagaParticipantReply}) vào outbox.
 * <p>
 * Tách phần dựng payload ra một nơi để các listener khác nhau có thể dùng chung
 * và đảm bảo schema v1 nhất quán.
 *
 * @see DeleteMemberSagaCommandListener
 * @see DeleteTreeSagaCommandListener
 */
@Component
public class SagaReplyStager {

    private final OutboxWriter outbox;

    /**
     * Khởi tạo stager.
     *
     * @param outbox writer outbox dùng để stage reply.
     */
    public SagaReplyStager(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    /**
     * Stage reply ACK cho orchestrator saga.
     *
     * @param operationId    UUID operation.
     * @param participant    tên participant.
     * @param stepCode       step code.
     * @param appliedVersion phiên bản aggregate đã áp dụng.
     * @param appliedEpoch   epoch đã áp dụng.
     * @param now            thời điểm phát sinh.
     */
    public void stageAck(UUID operationId, String participant, String stepCode,
                         long appliedVersion, long appliedEpoch, Instant now) {
        Map<String, Object> payload = basePayload(operationId, participant, stepCode, "ACK", now);
        payload.put("appliedAggregateVersion", appliedVersion);
        payload.put("appliedEpoch", appliedEpoch);
        stage(payload, participant);
    }

    /**
     * Stage reply FAILED cho orchestrator saga.
     *
     * @param envelope       envelope gốc để trích xuất operationId và treeId.
     * @param participant    tên participant.
     * @param stepCode       step code.
     * @param failureCode    mã lỗi (ví dụ {@code PARTICIPANT_FAILED}).
     * @param failureMessage thông điệp lỗi.
     */
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

    /**
     * Dựng payload cơ sở của một reply saga.
     *
     * @return map có các trường chung (operationId, participant, stepCode, status, occurredAt, schemaVersion).
     */
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

    /**
     * Stage payload vào outbox với topic {@code <participant>.replies.v1} và partition key = operationId.
     */
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