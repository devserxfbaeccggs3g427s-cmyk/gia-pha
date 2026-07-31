package com.familya.treeaccess.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.familya.platform.outbox.JdbcOutboxWriter;
import com.familya.platform.outbox.OutboxWriter;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Bộ stage phản hồi Saga lên outbox. Hai phương thức chính:
 *
 * <ul>
 *   <li>{@link #stageAck} để gửi ACK kèm revision/epoch đã áp dụng.</li>
 *   <li>{@link #stageFailed} để gửi FAILED kèm mã và thông điệp lỗi.</li>
 * </ul>
 *
 * <p>Mỗi message được stage sẽ mang eventType {@code SagaParticipantReply},
 * key là {@code operationId} để đảm bảo cùng một thao tác Saga đi vào một
 * partition Kafka duy nhất, bảo toàn thứ tự.</p>
 */
@Component
public class SagaReplyStager {

    /** OutboxWriter — điểm ghi để publisher vận chuyển lên Kafka. */
    private final OutboxWriter outbox;

    /**
     * Khởi tạo bộ stage reply Saga.
     *
     * @param outbox bộ ghi outbox dùng chung
     */
    public SagaReplyStager(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    /**
     * Stage phản hồi ACK cho một bước Saga. Payload bao gồm các thông tin cơ bản
     * cộng thêm {@code appliedAggregateVersion} và {@code appliedEpoch} do
     * use-case cung cấp.
     *
     * @param operationId    mã thao tác Saga
     * @param participant    tên service tham gia phản hồi
     * @param stepCode       mã bước đã hoàn tất
     * @param appliedVersion phiên bản tổng hợp mà participant đã áp dụng
     * @param appliedEpoch   epoch mà participant đã áp dụng
     * @param now            thời điểm phát sinh phản hồi
     */
    public void stageAck(UUID operationId, String participant, String stepCode,
                         long appliedVersion, long appliedEpoch, Instant now) {
        Map<String, Object> payload = basePayload(operationId, participant, stepCode, "ACK", now);
        payload.put("appliedAggregateVersion", appliedVersion);
        payload.put("appliedEpoch", appliedEpoch);
        stage(payload, participant);
    }

    /**
     * Stage phản hồi FAILED kèm mã lỗi và thông điệp. Phương thức này giữ nguyên
     * envelope ban đầu (chứa {@code treeId}) để phía Saga dễ truy vết.
     *
     * @param envelope       bản tin gốc đã nhận
     * @param participant    tên service tham gia phản hồi
     * @param stepCode       mã bước thất bại
     * @param failureCode    mã lỗi do service trả về
     * @param failureMessage thông điệp lỗi (có thể null)
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
     * Tạo phần payload cơ sở cho một reply Saga.
     *
     * @param operationId mã thao tác Saga
     * @param participant tên service tham gia
     * @param stepCode    mã bước
     * @param status      {@code "ACK"} hoặc {@code "FAILED"}
     * @param now         thời điểm xảy ra reply
     * @return map các trường payload
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
     * Stage payload lên outbox với topic {@code <participant>.replies.v1} và
     * gắn các header {@code eventType}, {@code operationId} để tiện truy vết.
     *
     * @param payload     payload đã được chuẩn hoá
     * @param participant tên service tham gia (quyết định topic reply)
     */
    private void stage(Map<String, Object> payload, String participant) {
        // Topic reply của mỗi participant là "<tên>.replies.v1".
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