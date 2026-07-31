package com.familya.relationship.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.familya.platform.outbox.JdbcOutboxWriter;
import com.familya.platform.outbox.OutboxWriter;
import com.familya.relationship.application.port.in.PurgeRelationshipTreeCommand;
import com.familya.relationship.application.port.in.RestoreRelationshipTreeCommand;
import com.familya.relationship.application.usecase.PurgeRelationshipTreeUseCase;
import com.familya.relationship.application.usecase.RestoreRelationshipTreeUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Listener Kafka cho các lệnh saga xóa cây của {@code Tree Access service}.
 * <p>
 * Topic: {@code tree.commands.v1}. Group ID: {@code relationship-service.delete-tree}.
 * Container factory: {@code sagaCommandListenerContainerFactory} (do platform cung cấp).
 * </p>
 *
 * <h2>Các bước xử lý</h2>
 * <ol>
 *   <li>Lọc theo {@code stepCode}: chỉ xử lý {@code PURGE_RELATIONSHIP_TREE}
 *       và {@code RESTORE_RELATIONSHIP_TREE}.</li>
 *   <li>Xác định đây là lệnh gốc hay lệnh bồi thường (compensation).</li>
 *   <li>Trích xuất {@code operationId}, {@code treeId}, {@code targetVersion},
 *       {@code targetEpoch} từ envelope JSON.</li>
 *   <li>Ủy quyền cho use case tương ứng; thu thập kết quả.</li>
 *   <li>Gửi phản hồi saga thông qua outbox (xem {@link #stageReply}).</li>
 * </ol>
 */
@Component
public class DeleteTreeSagaCommandListener {

    /** Logger ghi nhận hoạt động. */
    private static final Logger LOG = LoggerFactory.getLogger(DeleteTreeSagaCommandListener.class);

    /** Use case xử lý purge cây. */
    private final PurgeRelationshipTreeUseCase purgeUseCase;
    /** Use case xử lý khôi phục cây. */
    private final RestoreRelationshipTreeUseCase restoreUseCase;
    /** Cổng ghi outbox. */
    private final OutboxWriter outbox;

    /**
     * Khởi tạo listener.
     *
     * @param purgeUseCase   use case purge
     * @param restoreUseCase use case khôi phục
     * @param outbox         cổng ghi outbox
     */
    public DeleteTreeSagaCommandListener(PurgeRelationshipTreeUseCase purgeUseCase,
                                          RestoreRelationshipTreeUseCase restoreUseCase,
                                          OutboxWriter outbox) {
        this.purgeUseCase = purgeUseCase;
        this.restoreUseCase = restoreUseCase;
        this.outbox = outbox;
    }

    /**
     * Hàm xử lý một envelope JSON từ Kafka.
     * <p>
     * Có thể ném {@link RuntimeException} khi thất bại. Nếu là lệnh gốc (không
     * phải compensation) thì sẽ rethrow để Kafka container xử lý retry/DLQ;
     * nếu là compensation thì chỉ log và stage phản hồi FAILED.
     * </p>
     *
     * @param envelope JSON đại diện cho lệnh saga
     */
    @KafkaListener(
            topics = "tree.commands.v1",
            groupId = "relationship-service.delete-tree",
            containerFactory = "sagaCommandListenerContainerFactory")
    public void onCommand(JsonNode envelope) {
        // Bước 1: lấy stepCode - chỉ xử lý các bước liên quan tới Relationship service.
        String stepCode = envelope.path("stepCode").asText("");
        if (!"PURGE_RELATIONSHIP_TREE".equals(stepCode) && !"RESTORE_RELATIONSHIP_TREE".equals(stepCode)) {
            // Không phải lệnh dành cho service này - bỏ qua để consumer khác xử lý.
            return;
        }
        // Bước 2: xác định đây là lệnh gốc hay bồi thường.
        // Compensation nếu isCompensation=true hoặc stepCode là RESTORE.
        boolean compensation = envelope.path("isCompensation").asBoolean(false)
                || "RESTORE_RELATIONSHIP_TREE".equals(stepCode);
        try {
            // Bước 3: trích xuất các trường cần thiết từ envelope.
            UUID operationId = UUID.fromString(envelope.path("operationId").asText());
            UUID treeId = UUID.fromString(envelope.path("treeId").asText());
            long targetVersion = envelope.path("targetAggregateVersion").asLong();
            long targetEpoch = envelope.path("targetEpoch").asLong();
            long appliedVersion; long appliedEpoch;
            if (compensation) {
                // Bước 4a: thực thi khôi phục.
                RestoreRelationshipTreeUseCase.Result r = restoreUseCase.execute(
                        new RestoreRelationshipTreeCommand(operationId, treeId));
                appliedVersion = r.appliedAggregateVersion();
                appliedEpoch = r.appliedEpoch();
            } else {
                // Bước 4b: thực thi purge.
                PurgeRelationshipTreeUseCase.Result r = purgeUseCase.execute(
                        new PurgeRelationshipTreeCommand(operationId, treeId, targetVersion, targetEpoch));
                appliedVersion = r.appliedAggregateVersion();
                appliedEpoch = r.appliedEpoch();
            }
            // Bước 5: stage phản hồi ACK thông qua outbox.
            stageReply(operationId, "relationship-service", stepCode, "ACK",
                    appliedVersion, appliedEpoch, envelope);
        } catch (RuntimeException ex) {
            // Nếu xử lý thất bại: log + stage phản hồi FAILED.
            // Lưu ý: với lệnh gốc, ngoại lệ được rethrow để Kafka retry/DLQ.
            LOG.error("Failed to process delete-tree Saga command stepCode={}", stepCode, ex);
            stageReply(UUID.fromString(envelope.path("operationId").asText()),
                    "relationship-service", stepCode, "FAILED", 0L, 0L, envelope);
            throw ex;
        }
    }

    /**
     * Stage một phản hồi saga lên outbox.
     * <p>
     * Phản hồi được phát hành qua topic {@code relationship.replies.v1} và
     * partition theo {@code treeId} để orchestrator có thể đồng bộ.
     * </p>
     *
     * @param operationId    định danh thao tác saga
     * @param participant    tên service tham gia (relationship-service)
     * @param stepCode       mã bước saga
     * @param status         trạng thái (ACK / FAILED)
     * @param appliedVersion phiên bản aggregate đã áp dụng
     * @param appliedEpoch   epoch đã áp dụng
     * @param envelope       JSON envelope gốc (để lấy treeId)
     */
    private void stageReply(UUID operationId, String participant, String stepCode,
                            String status, long appliedVersion, long appliedEpoch,
                            JsonNode envelope) {
        // Lấy treeId từ envelope (bắt buộc cho việc partition).
        UUID treeId = UUID.fromString(envelope.path("treeId").asText());
        // Payload JSON theo schema v1 của phản hồi saga.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operationId", operationId.toString());
        payload.put("participantService", participant);
        payload.put("stepCode", stepCode);
        payload.put("status", status);
        payload.put("appliedAggregateVersion", appliedVersion);
        payload.put("appliedEpoch", appliedEpoch);
        payload.put("treeId", treeId.toString());
        // failureCode chỉ có giá trị khi status=FAILED.
        payload.put("failureCode", status.equals("FAILED") ? "PARTICIPANT_FAILED" : null);
        payload.put("failureMessage", "");
        payload.put("occurredAtEpochMs", Instant.now().toEpochMilli());
        payload.put("schemaVersion", "v1");
        // Cấu hình outbox writer với aggregate="saga-reply".
        JdbcOutboxWriter.Builder b = JdbcOutboxWriter.builder()
                .create("saga-reply", operationId.toString(), 1L,
                        "SagaParticipantReply", 1,
                        "relationship.replies.v1", treeId.toString(), payload);
        // Headers giúp orchestrator lọc và routing.
        b.header("eventType", "SagaParticipantReply");
        b.header("operationId", operationId.toString());
        b.header("treeId", treeId.toString());
        outbox.stage(b.build());
    }
}