package com.familya.media.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.familya.media.application.port.in.PurgeMediaMetadataTreeCommand;
import com.familya.media.application.port.in.RestoreMediaMetadataTreeCommand;
import com.familya.media.application.usecase.PurgeMediaMetadataTreeUseCase;
import com.familya.media.application.usecase.RestoreMediaMetadataTreeUseCase;
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
 * Adapter Kafka đầu vào (inbound) — listener tham gia Saga "delete-tree".
 * <p>
 * Lắng nghe lệnh điều phối trên topic {@code tree.commands.v1} cho 2 step media-service
 * phụ trách: {@code PURGE_MEDIA_METADATA_TREE} và {@code RESTORE_MEDIA_METADATA_TREE}.
 * Listener thực thi use case tương ứng và stage reply vào outbox.
 * <p>
 * Hợp đồng thông điệp (envelope JSON):
 * <ul>
 *   <li>{@code operationId}, {@code treeId} (UUID)</li>
 *   <li>{@code targetAggregateVersion}, {@code targetEpoch} (long)</li>
 *   <li>{@code placeRetentionHolds} (boolean, mặc định true)</li>
 *   <li>{@code isCompensation} (boolean)</li>
 * </ul>
 * Khi stepCode là {@code RESTORE_*} thì mặc định coi như compensation bất kể cờ.
 */
@Component
public class DeleteTreeSagaCommandListener {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteTreeSagaCommandListener.class);

    private final PurgeMediaMetadataTreeUseCase purgeUseCase;
    private final RestoreMediaMetadataTreeUseCase restoreUseCase;
    private final OutboxWriter outbox;

    /**
     * Khởi tạo listener.
     *
     * @param purgeUseCase  use case purge metadata media theo tree (kèm đặt retention hold).
     * @param restoreUseCase use case khôi phục metadata media (compensation).
     * @param outbox        writer outbox dùng để stage reply.
     */
    public DeleteTreeSagaCommandListener(PurgeMediaMetadataTreeUseCase purgeUseCase,
                                         RestoreMediaMetadataTreeUseCase restoreUseCase,
                                         OutboxWriter outbox) {
        this.purgeUseCase = purgeUseCase;
        this.restoreUseCase = restoreUseCase;
        this.outbox = outbox;
    }

    /**
     * Xử lý một envelope lệnh saga từ {@code tree.commands.v1}.
     * <p>
     * Luồng:
     * <ol>
     *   <li>Lọc theo {@code stepCode} — chỉ xử lý 2 step media-service.</li>
     *   <li>Xác định chiều bù trừ (compensation) dựa trên cờ hoặc stepCode.</li>
     *   <li>Thực thi use case (purge hoặc restore) và stage ACK.</li>
     *   <li>Thất bại: stage FAILED và ném lại để retry/DLQ.</li>
     * </ol>
     *
     * @param envelope payload JSON lệnh saga.
     */
    @KafkaListener(
            topics = "tree.commands.v1",
            groupId = "media-service.delete-tree",
            containerFactory = "sagaCommandListenerContainerFactory")
    public void onCommand(JsonNode envelope) {
        String stepCode = envelope.path("stepCode").asText("");
        // Bỏ qua các step không thuộc media-service.
        if (!"PURGE_MEDIA_METADATA_TREE".equals(stepCode)
                && !"RESTORE_MEDIA_METADATA_TREE".equals(stepCode)) {
            return;
        }
        // Compensation nếu orchestrator đánh dấu HOẶC stepCode đã là restore.
        boolean compensation = envelope.path("isCompensation").asBoolean(false)
                || "RESTORE_MEDIA_METADATA_TREE".equals(stepCode);
        try {
            UUID operationId = UUID.fromString(envelope.path("operationId").asText());
            UUID treeId = UUID.fromString(envelope.path("treeId").asText());
            long targetVersion = envelope.path("targetAggregateVersion").asLong();
            long targetEpoch = envelope.path("targetEpoch").asLong();
            // Mặc định đặt retention hold để tuân thủ yêu cầu lưu trữ pháp lý.
            boolean placeHolds = envelope.path("placeRetentionHolds").asBoolean(true);
            long appliedVersion; long appliedEpoch;
            if (compensation) {
                // Bù trừ: khôi phục metadata media đã purge (nếu purge có khả năng phục hồi).
                RestoreMediaMetadataTreeUseCase.Result r = restoreUseCase.execute(
                        new RestoreMediaMetadataTreeCommand(operationId, treeId));
                appliedVersion = r.appliedAggregateVersion();
                appliedEpoch = r.appliedEpoch();
            } else {
                // Tiến: purge toàn bộ metadata media của cây.
                PurgeMediaMetadataTreeUseCase.Result r = purgeUseCase.execute(
                        new PurgeMediaMetadataTreeCommand(operationId, treeId, placeHolds,
                                targetVersion, targetEpoch));
                appliedVersion = r.appliedAggregateVersion();
                appliedEpoch = r.appliedEpoch();
            }
            // Trả ACK cho orchestrator qua outbox.
            stageReply(operationId, "media-service", stepCode, "ACK",
                    appliedVersion, appliedEpoch, envelope);
        } catch (RuntimeException ex) {
            LOG.error("Failed to process delete-tree Saga command stepCode={}", stepCode, ex);
            // Stage FAILED rồi ném lại cho Kafka retry/DLQ.
            stageReply(UUID.fromString(envelope.path("operationId").asText()),
                    "media-service", stepCode, "FAILED", 0L, 0L, envelope);
            throw ex;
        }
    }

    /**
     * Stage reply SagaParticipantReply vào outbox cho orchestrator.
     *
     * @param operationId    UUID operation do orchestrator cấp.
     * @param participant    tên participant ("media-service").
     * @param stepCode       step code.
     * @param status         "ACK" hoặc "FAILED".
     * @param appliedVersion phiên bản aggregate đã áp dụng.
     * @param appliedEpoch   epoch đã áp dụng.
     * @param envelope       envelope gốc (chỉ dùng để lấy treeId).
     */
    private void stageReply(UUID operationId, String participant, String stepCode,
                            String status, long appliedVersion, long appliedEpoch,
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
        // Failure code chỉ đặt khi FAILED, ngược lại null để giữ payload sạch.
        payload.put("failureCode", status.equals("FAILED") ? "PARTICIPANT_FAILED" : null);
        payload.put("failureMessage", "");
        payload.put("occurredAtEpochMs", Instant.now().toEpochMilli());
        payload.put("schemaVersion", "v1");
        // Partition key = treeId để đảm bảo thứ tự theo cây.
        JdbcOutboxWriter.Builder b = JdbcOutboxWriter.builder()
                .create("saga-reply", operationId.toString(), 1L,
                        "SagaParticipantReply", 1,
                        "media.replies.v1", treeId.toString(), payload);
        b.header("eventType", "SagaParticipantReply");
        b.header("operationId", operationId.toString());
        b.header("treeId", treeId.toString());
        outbox.stage(b.build());
    }
}