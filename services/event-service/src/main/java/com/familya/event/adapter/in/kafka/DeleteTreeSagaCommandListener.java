package com.familya.event.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.familya.event.application.port.in.PurgeEventTreeCommand;
import com.familya.event.application.port.in.RestoreEventTreeCommand;
import com.familya.event.application.usecase.PurgeEventTreeUseCase;
import com.familya.event.application.usecase.RestoreEventTreeUseCase;
import com.familya.platform.outbox.JdbcOutboxWriter;
import com.familya.platform.outbox.OutboxWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka listener cho các lệnh của <b>delete-tree Saga</b> trong
 * event-service.
 *
 * <p>Lắng nghe trên topic {@code tree.commands.v1} với group
 * {@code event-service.delete-tree} và container factory
 * {@code sagaCommandListenerContainerFactory}.
 *
 * <h2>Các bước Saga xử lý</h2>
 * <ul>
 *   <li>{@code PURGE_EVENT_TREE}: gọi {@link PurgeEventTreeUseCase} để
 *       tombstone hàng loạt.</li>
 *   <li>{@code RESTORE_EVENT_TREE}: gọi {@link RestoreEventTreeUseCase}
 *       để phục hồi (khi Saga rollback).</li>
 * </ul>
 *
 * <h2>Luồng xử lý</h2>
 * <ol>
 *   <li>Đọc {@code stepCode}; bỏ qua nếu không phải hai bước trên.</li>
 *   <li>Xác định {@code isCompensation} từ envelope.</li>
 *   <li>Parse các trường bắt buộc: {@code operationId}, {@code treeId},
 *       {@code targetAggregateVersion}, {@code targetEpoch}.</li>
 *   <li>Gọi use case tương ứng và stage phản hồi.</li>
 *   <li>Nếu lỗi, stage phản hồi {@code FAILED} rồi ném lại.</li>
 * </ol>
 *
 * @author gia-pha platform
 */
@Component
public class DeleteTreeSagaCommandListener {

    /** Logger dùng cho audit. */
    private static final Logger LOG = LoggerFactory.getLogger(DeleteTreeSagaCommandListener.class);

    private final PurgeEventTreeUseCase purgeUseCase;
    private final RestoreEventTreeUseCase restoreUseCase;
    private final OutboxWriter outbox;

    /**
     * Khởi tạo listener.
     *
     * @param purgeUseCase   use case purge cây.
     * @param restoreUseCase use case khôi phục cây.
     * @param outbox         writer ghi vào bảng outbox.
     */
    public DeleteTreeSagaCommandListener(PurgeEventTreeUseCase purgeUseCase,
                                          RestoreEventTreeUseCase restoreUseCase,
                                          OutboxWriter outbox) {
        this.purgeUseCase = purgeUseCase;
        this.restoreUseCase = restoreUseCase;
        this.outbox = outbox;
    }

    /**
     * Xử lý một envelope lệnh Saga. Sơ đồ tương tự
     * {@link DeleteMemberSagaCommandListener#onCommand(JsonNode)}.
     *
     * @param envelope JSON envelope từ Kafka.
     */
    @KafkaListener(
            topics = "tree.commands.v1",
            groupId = "event-service.delete-tree",
            containerFactory = "sagaCommandListenerContainerFactory")
    public void onCommand(JsonNode envelope) {
        // Bước 1: lọc stepCode — chỉ xử lý hai bước event-service quan tâm.
        String stepCode = envelope.path("stepCode").asText("");
        if (!"PURGE_EVENT_TREE".equals(stepCode) && !"RESTORE_EVENT_TREE".equals(stepCode)) {
            return;
        }

        // Bước 2: xác định compensation từ envelope hoặc từ tên stepCode.
        boolean compensation = envelope.path("isCompensation").asBoolean(false)
                || "RESTORE_EVENT_TREE".equals(stepCode);

        try {
            // Bước 3: parse các trường bắt buộc.
            UUID operationId = UUID.fromString(envelope.path("operationId").asText());
            UUID treeId = UUID.fromString(envelope.path("treeId").asText());
            long targetVersion = envelope.path("targetAggregateVersion").asLong();
            long targetEpoch = envelope.path("targetEpoch").asLong();
            long appliedVersion;
            long appliedEpoch;

            if (compensation) {
                // Bước 4a: chạy use case restore.
                RestoreEventTreeUseCase.Result r = restoreUseCase.execute(
                        new RestoreEventTreeCommand(operationId, treeId));
                appliedVersion = r.appliedAggregateVersion();
                appliedEpoch = r.appliedEpoch();
            } else {
                // Bước 4b: chạy use case purge.
                PurgeEventTreeUseCase.Result r = purgeUseCase.execute(
                        new PurgeEventTreeCommand(operationId, treeId, targetVersion, targetEpoch));
                appliedVersion = r.appliedAggregateVersion();
                appliedEpoch = r.appliedEpoch();
            }

            // Bước 5: stage phản hồi ACK.
            stageReply(operationId, "event-service", stepCode, "ACK",
                    appliedVersion, appliedEpoch, envelope);
        } catch (RuntimeException ex) {
            // Bước 6: log + stage phản hồi FAILED + ném lại để retry.
            LOG.error("Failed to process delete-tree Saga command stepCode={}", stepCode, ex);
            stageReply(UUID.fromString(envelope.path("operationId").asText()),
                    "event-service", stepCode, "FAILED", 0L, 0L, envelope);
            throw ex;
        }
    }

    /**
     * Stage phản hồi Saga. Payload được xây dựng đồng nhất với schema
     * {@code SagaParticipantReply v1}.
     *
     * @param operationId    định danh Saga.
     * @param participant    tên participant.
     * @param stepCode       mã bước Saga.
     * @param status         trạng thái: {@code ACK} hoặc {@code FAILED}.
     * @param appliedVersion phiên bản đã áp dụng.
     * @param appliedEpoch   epoch đã áp dụng.
     * @param envelope       envelope gốc (chứa treeId).
     */
    private void stageReply(UUID operationId, String participant, String stepCode,
                            String status, long appliedVersion, long appliedEpoch,
                            JsonNode envelope) {
        // Trích treeId để gắn partition key.
        UUID treeId = UUID.fromString(envelope.path("treeId").asText());

        // Payload schema SagaParticipantReply v1.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operationId", operationId.toString());
        payload.put("participantService", participant);
        payload.put("stepCode", stepCode);
        payload.put("status", status);
        payload.put("appliedAggregateVersion", appliedVersion);
        payload.put("appliedEpoch", appliedEpoch);
        payload.put("treeId", treeId.toString());
        // failureCode chỉ đặt khi FAILED — null cho ACK để phía tiêu thụ phân biệt rõ.
        payload.put("failureCode", status.equals("FAILED") ? "PARTICIPANT_FAILED" : null);
        payload.put("failureMessage", "");
        payload.put("occurredAtEpochMs", Instant.now().toEpochMilli());
        payload.put("schemaVersion", "v1");

        // Tạo builder và stage vào outbox.
        JdbcOutboxWriter.Builder b = JdbcOutboxWriter.builder()
                .create("saga-reply", operationId.toString(), 1L,
                        "SagaParticipantReply", 1,
                        "event.replies.v1", treeId.toString(), payload);
        b.header("eventType", "SagaParticipantReply");
        b.header("operationId", operationId.toString());
        b.header("treeId", treeId.toString());
        outbox.stage(b.build());
    }
}
