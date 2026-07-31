package com.familya.relationship.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.familya.platform.outbox.JdbcOutboxWriter;
import com.familya.platform.outbox.OutboxWriter;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Helper component dùng để stage các phản hồi saga lên outbox.
 * <p>
 * Phản hồi được gửi tới topic {@code <participant>.replies.v1} và partition theo
 * {@code operationId}. Worker outbox của platform sẽ đẩy các bản ghi này lên
 * Kafka để orchestrator có thể đồng bộ tiến trình saga.
 * </p>
 *
 * <p>
 * Lớp này được dùng bởi các listener saga; nó tách phần "ghi phản hồi" ra khỏi
 * logic xử lý lệnh để dễ kiểm thử và tái sử dụng.
 * </p>
 */
@Component
public class SagaReplyStager {

    /** Cổng ghi outbox. */
    private final OutboxWriter outbox;

    /**
     * Khởi tạo helper.
     *
     * @param outbox cổng ghi outbox
     */
    public SagaReplyStager(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    /**
     * Stage phản hồi ACK với phiên bản aggregate và epoch đã áp dụng.
     *
     * @param operationId    định danh thao tác saga
     * @param participant    tên service tham gia
     * @param stepCode       mã bước saga
     * @param appliedVersion phiên bản aggregate đã áp dụng
     * @param appliedEpoch   epoch đã áp dụng
     * @param now            thời điểm hiện tại (cho trường occurredAtEpochMs)
     */
    public void stageAck(UUID operationId, String participant, String stepCode,
                         long appliedVersion, long appliedEpoch, Instant now) {
        Map<String, Object> payload = basePayload(operationId, participant, stepCode, "ACK", now);
        payload.put("appliedAggregateVersion", appliedVersion);
        payload.put("appliedEpoch", appliedEpoch);
        stage(payload, participant);
    }

    /**
     * Stage phản hồi FAILED với mã lỗi và thông điệp.
     *
     * @param envelope        JSON envelope gốc (chứa operationId và treeId)
     * @param participant     tên service tham gia
     * @param stepCode        mã bước saga
     * @param failureCode     mã lỗi (ví dụ: {@code PARTICIPANT_FAILED})
     * @param failureMessage  thông điệp lỗi (có thể null)
     */
    public void stageFailed(JsonNode envelope, String participant, String stepCode,
                            String failureCode, String failureMessage) {
        Map<String, Object> payload = basePayload(
                UUID.fromString(envelope.path("operationId").asText()),
                participant, stepCode, "FAILED", Instant.now());
        payload.put("failureCode", failureCode);
        // Đảm bảo failureMessage không null (dù là chuỗi rỗng) để tránh lỗi tuần tự hóa.
        payload.put("failureMessage", failureMessage == null ? "" : failureMessage);
        payload.put("treeId", envelope.path("treeId").asText());
        stage(payload, participant);
    }

    /**
     * Tạo payload cơ sở cho phản hồi saga.
     *
     * @param operationId định danh thao tác saga
     * @param participant tên service tham gia
     * @param stepCode    mã bước saga
     * @param status      trạng thái (ACK / FAILED)
     * @param now         thời điểm hiện tại
     * @return payload cơ sở dưới dạng {@code LinkedHashMap} để giữ thứ tự trường
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
     * Ghi payload lên outbox với aggregate {@code saga-reply} và topic
     * {@code <participant>.replies.v1} (partition theo operationId).
     *
     * @param payload     payload đã chuẩn bị
     * @param participant tên service tham gia (dùng để sinh topic)
     */
    private void stage(Map<String, Object> payload, String participant) {
        // Topic theo convention: <participant>.replies.v1.
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