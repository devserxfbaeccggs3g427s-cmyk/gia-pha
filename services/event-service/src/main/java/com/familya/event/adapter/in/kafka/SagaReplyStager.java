package com.familya.event.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.familya.platform.outbox.JdbcOutboxWriter;
import com.familya.platform.outbox.OutboxWriter;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tiện ích dùng chung để <b>stage</b> các bản ghi phản hồi Saga
 * ({@code SagaParticipantReply}) vào outbox.
 *
 * <p>Được sử dụng khi cần xây dựng payload theo schema thống nhất
 * {@code v1}. Hai phương thức chính:
 * <ul>
 *   <li>{@link #stageAck}: tạo reply thành công.</li>
 *   <li>{@link #stageFailed}: tạo reply thất bại từ một envelope gốc.</li>
 * </ul>
 *
 * <p>Việc thống nhất payload đảm bảo các phía tiêu thụ (orchestrator,
 * dashboard audit) có thể parse nhất quán.
 *
 * @author gia-pha platform
 */
@Component
public class SagaReplyStager {

    private final OutboxWriter outbox;

    /**
     * Khởi tạo stager.
     *
     * @param outbox writer ghi vào bảng outbox.
     */
    public SagaReplyStager(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    /**
     * Stage phản hồi thành công ({@code ACK}) cho một operation.
     *
     * @param operationId    định danh Saga.
     * @param participant    tên participant phát phản hồi.
     * @param stepCode       mã bước Saga đã hoàn tất.
     * @param appliedVersion phiên bản đã áp dụng.
     * @param appliedEpoch   epoch đã áp dụng.
     * @param now            thời điểm phát hành phản hồi.
     */
    public void stageAck(UUID operationId, String participant, String stepCode,
                         long appliedVersion, long appliedEpoch, Instant now) {
        Map<String, Object> payload = basePayload(operationId, participant, stepCode, "ACK", now);
        payload.put("appliedAggregateVersion", appliedVersion);
        payload.put("appliedEpoch", appliedEpoch);
        stage(payload, participant);
    }

    /**
     * Stage phản hồi thất bại ({@code FAILED}) cho một envelope gốc.
     *
     * @param envelope       envelope gốc (cung cấp operationId, treeId).
     * @param participant    tên participant.
     * @param stepCode       mã bước Saga.
     * @param failureCode    mã lỗi nghiệp vụ.
     * @param failureMessage thông điệp lỗi (có thể {@code null}).
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
     * Tạo phần payload chung cho mọi phản hồi.
     *
     * @param operationId định danh Saga.
     * @param participant tên participant.
     * @param stepCode    mã bước.
     * @param status      trạng thái.
     * @param now         thời điểm phát hành.
     * @return map chứa các trường chung.
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
     * Stage payload vào outbox. Topic mặc định: {@code <participant>.replies.v1}.
     *
     * @param payload     payload đã xây dựng xong.
     * @param participant tên participant — dùng trong tên topic.
     */
    private void stage(Map<String, Object> payload, String participant) {
        // Quy ước topic: <participant>.replies.v1, ví dụ event-service.replies.v1.
        String topic = participant + ".replies.v1";
        String operationId = (String) payload.get("operationId");

        // Tạo builder; partition key là operationId để dễ consume theo Saga.
        JdbcOutboxWriter.Builder b = JdbcOutboxWriter.builder()
                .create("saga-reply", operationId, 1L,
                        "SagaParticipantReply", 1, topic, operationId, payload);
        b.header("eventType", "SagaParticipantReply");
        b.header("operationId", operationId);
        outbox.stage(b.build());
    }
}
