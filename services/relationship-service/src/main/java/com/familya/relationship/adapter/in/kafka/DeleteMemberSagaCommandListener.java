package com.familya.relationship.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.familya.platform.outbox.JdbcOutboxWriter;
import com.familya.platform.outbox.OutboxWriter;
import com.familya.relationship.application.port.in.DisableMemberRelationshipsCommand;
import com.familya.relationship.application.port.in.RestoreMemberRelationshipsCommand;
import com.familya.relationship.application.usecase.DisableMemberRelationshipsUseCase;
import com.familya.relationship.application.usecase.RestoreMemberRelationshipsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Listener Kafka cho các lệnh saga xóa thành viên của {@code Member service}.
 * <p>
 * Topic: {@code member.commands.v1}. Group ID: {@code relationship-service.delete-member}.
 * Container factory: {@code sagaCommandListenerContainerFactory} (do platform cung cấp).
 * </p>
 *
 * <h2>Các bước xử lý</h2>
 * <ol>
 *   <li>Lọc theo {@code stepCode}: chỉ xử lý {@code DISABLE_RELATIONSHIPS} và
 *       {@code RESTORE_MEMBER_RELATIONSHIPS}.</li>
 *   <li>Xác định đây là lệnh gốc hay compensation.</li>
 *   <li>Trích xuất các trường từ envelope JSON.</li>
 *   <li>Ủy quyền cho use case tương ứng.</li>
 *   <li>Stage phản hồi ACK/FAILED thông qua outbox.</li>
 * </ol>
 */
@Component
public class DeleteMemberSagaCommandListener {

    /** Logger ghi nhận hoạt động. */
    private static final Logger LOG = LoggerFactory.getLogger(DeleteMemberSagaCommandListener.class);

    /** Use case xử lý disable. */
    private final DisableMemberRelationshipsUseCase disableUseCase;
    /** Use case xử lý khôi phục. */
    private final RestoreMemberRelationshipsUseCase restoreUseCase;
    /** Cổng ghi outbox. */
    private final OutboxWriter outbox;

    /**
     * Khởi tạo listener.
     *
     * @param disableUseCase  use case disable
     * @param restoreUseCase  use case khôi phục
     * @param outbox          cổng ghi outbox
     */
    public DeleteMemberSagaCommandListener(DisableMemberRelationshipsUseCase disableUseCase,
                                            RestoreMemberRelationshipsUseCase restoreUseCase,
                                            OutboxWriter outbox) {
        this.disableUseCase = disableUseCase;
        this.restoreUseCase = restoreUseCase;
        this.outbox = outbox;
    }

    /**
     * Hàm xử lý một envelope JSON từ Kafka.
     *
     * @param envelope JSON đại diện cho lệnh saga
     */
    @KafkaListener(
            topics = "member.commands.v1",
            groupId = "relationship-service.delete-member",
            containerFactory = "sagaCommandListenerContainerFactory")
    public void onCommand(JsonNode envelope) {
        // Bước 1: lọc theo stepCode - chỉ xử lý các bước liên quan tới service này.
        String stepCode = envelope.path("stepCode").asText("");
        if (!"DISABLE_RELATIONSHIPS".equals(stepCode)
                && !"RESTORE_MEMBER_RELATIONSHIPS".equals(stepCode)) {
            return;
        }
        // Bước 2: xác định lệnh gốc hay bồi thường.
        boolean compensation = envelope.path("isCompensation").asBoolean(false)
                || "RESTORE_MEMBER_RELATIONSHIPS".equals(stepCode);
        try {
            // Bước 3: trích xuất các trường cần thiết.
            UUID operationId = UUID.fromString(envelope.path("operationId").asText());
            UUID treeId = UUID.fromString(envelope.path("treeId").asText());
            UUID memberId = UUID.fromString(envelope.path("memberId").asText());
            long targetVersion = envelope.path("targetAggregateVersion").asLong();
            long targetEpoch = envelope.path("targetEpoch").asLong();

            long appliedVersion;
            long appliedEpoch;
            if (compensation) {
                // Bước 4a: thực thi khôi phục.
                RestoreMemberRelationshipsUseCase.Result r = restoreUseCase.execute(
                        new RestoreMemberRelationshipsCommand(operationId, treeId, memberId));
                appliedVersion = r.appliedAggregateVersion();
                appliedEpoch = r.appliedEpoch();
            } else {
                // Bước 4b: thực thi disable - truyền expectedVersion/expectedEpoch=0 vì
                // orchestrator đã đồng bộ trước đó.
                DisableMemberRelationshipsUseCase.Result r = disableUseCase.execute(
                        new DisableMemberRelationshipsCommand(operationId, treeId, memberId,
                                0L, 0L, targetVersion, targetEpoch));
                appliedVersion = r.appliedAggregateVersion();
                appliedEpoch = r.appliedEpoch();
            }
            // Bước 5: stage phản hồi ACK.
            stageReply(operationId, "relationship-service", stepCode, "ACK",
                    appliedVersion, appliedEpoch, null, null, envelope);
        } catch (RuntimeException ex) {
            // Lỗi xử lý: log + stage phản hồi FAILED.
            // Lưu ý: với lệnh compensation, không rethrow để tránh vòng lặp retry vô ích
            // (compensation thất bại sẽ được orchestrator xử lý riêng).
            LOG.error("Failed to process delete-member Saga command stepCode={}", stepCode, ex);
            stageReply(UUID.fromString(envelope.path("operationId").asText()),
                    "relationship-service", stepCode, "FAILED", 0L, 0L,
                    "PARTICIPANT_FAILED", ex.getMessage(), envelope);
            if (!compensation) throw ex;
        }
    }

    /**
     * Stage phản hồi saga lên outbox với đầy đủ thông tin (kể cả lỗi).
     *
     * @param operationId    định danh thao tác saga
     * @param participant    tên service tham gia
     * @param stepCode       mã bước saga
     * @param status         trạng thái (ACK / FAILED)
     * @param appliedVersion phiên bản aggregate đã áp dụng
     * @param appliedEpoch   epoch đã áp dụng
     * @param failureCode    mã lỗi (null nếu ACK)
     * @param failureMessage thông điệp lỗi (null nếu ACK)
     * @param envelope       JSON envelope gốc
     */
    private void stageReply(UUID operationId, String participant, String stepCode,
                            String status, long appliedVersion, long appliedEpoch,
                            String failureCode, String failureMessage,
                            JsonNode envelope) {
        UUID treeId = UUID.fromString(envelope.path("treeId").asText());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operationId", operationId.toString());
        payload.put("participantService", participant);
        payload.put("stepCode", stepCode);
        payload.put("status", status);
        payload.put("appliedAggregateVersion", appliedVersion);
        payload.put("appliedEpoch", appliedEpoch);
        payload.put("treeId", treeId.toString());
        payload.put("failureCode", failureCode);
        payload.put("failureMessage", failureMessage == null ? "" : failureMessage);
        payload.put("occurredAtEpochMs", Instant.now().toEpochMilli());
        payload.put("schemaVersion", "v1");
        JdbcOutboxWriter.Builder b = JdbcOutboxWriter.builder()
                .create("saga-reply", operationId.toString(), 1L,
                        "SagaParticipantReply", 1,
                        "relationship.replies.v1", treeId.toString(), payload);
        b.header("eventType", "SagaParticipantReply");
        b.header("operationId", operationId.toString());
        b.header("treeId", treeId.toString());
        outbox.stage(b.build());
    }
}