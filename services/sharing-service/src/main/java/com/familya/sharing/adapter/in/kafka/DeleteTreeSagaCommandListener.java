package com.familya.sharing.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.familya.platform.outbox.JdbcOutboxWriter;
import com.familya.platform.outbox.OutboxWriter;
import com.familya.sharing.application.port.in.RevokeSharingTreeCommand;
import com.familya.sharing.application.port.in.RestoreSharingTreeCommand;
import com.familya.sharing.application.usecase.RevokeSharingTreeUseCase;
import com.familya.sharing.application.usecase.RestoreSharingTreeUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Listener Kafka cho các lệnh (commands) của saga xóa cây gia phả mà
 * sharing-service là participant.
 * <p>
 * Topic: {@code tree.commands.v1}. Hai loại bước mà listener này xử lý:
 * <ul>
 *     <li>{@code REVOKE_SHARING_TREE} &mdash; thu hồi tất cả liên kết chia sẻ
 *         của cây.</li>
 *     <li>{@code RESTORE_SHARING_TREE} &mdash; khôi phục các liên kết đã bị
 *         thu hồi bởi saga trước đó.</li>
 * </ul>
 * Mọi lệnh khác sẽ bị bỏ qua (trả về sớm). Sau khi xử lý, listener sẽ stage
 * một {@code SagaParticipantReply} vào outbox để saga có thể tiếp tục.
 */
@Component
public class DeleteTreeSagaCommandListener {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteTreeSagaCommandListener.class);

    private final RevokeSharingTreeUseCase revokeUseCase;
    private final RestoreSharingTreeUseCase restoreUseCase;
    private final OutboxWriter outbox;

    /**
     * Khởi tạo listener với các use case cần thiết.
     *
     * @param revokeUseCase  use case thu hồi tất cả liên kết của cây.
     * @param restoreUseCase use case khôi phục liên kết.
     * @param outbox         cổng ghi outbox.
     */
    public DeleteTreeSagaCommandListener(RevokeSharingTreeUseCase revokeUseCase,
                                         RestoreSharingTreeUseCase restoreUseCase,
                                         OutboxWriter outbox) {
        this.revokeUseCase = revokeUseCase;
        this.restoreUseCase = restoreUseCase;
        this.outbox = outbox;
    }

    /**
     * Hàm xử lý chính, được Spring Kafka gọi cho mỗi bản ghi trên topic
     * {@code tree.commands.v1}.
     *
     * @param envelope bản tin JSON chứa các trường: {@code stepCode},
     *                 {@code isCompensation}, {@code operationId}, {@code treeId},
     *                 {@code targetAggregateVersion}, {@code targetEpoch}.
     */
    @KafkaListener(
            topics = "tree.commands.v1",
            groupId = "sharing-service.delete-tree",
            containerFactory = "sagaCommandListenerContainerFactory")
    public void onCommand(JsonNode envelope) {
        // Bước 1: Đọc stepCode; bỏ qua nếu không phải lệnh dành cho sharing-service.
        String stepCode = envelope.path("stepCode").asText("");
        if (!"REVOKE_SHARING_TREE".equals(stepCode) && !"RESTORE_SHARING_TREE".equals(stepCode)) {
            return;
        }

        // Bước 2: Xác định đây là bước bù trừ (compensation) hay bước thuận.
        // RESTORE luôn là compensation; REVOKE chỉ là compensation khi cờ tương ứng bật.
        boolean compensation = envelope.path("isCompensation").asBoolean(false)
                || "RESTORE_SHARING_TREE".equals(stepCode);
        try {
            // Bước 3: Trích xuất các tham số cần thiết từ envelope.
            UUID operationId = UUID.fromString(envelope.path("operationId").asText());
            UUID treeId = UUID.fromString(envelope.path("treeId").asText());
            long targetVersion = envelope.path("targetAggregateVersion").asLong();
            long targetEpoch = envelope.path("targetEpoch").asLong();

            long appliedVersion; long appliedEpoch;
            if (compensation) {
                // Bước 4a: Compensation &mdash; khôi phục liên kết.
                RestoreSharingTreeUseCase.Result r = restoreUseCase.execute(
                        new RestoreSharingTreeCommand(operationId, treeId));
                appliedVersion = r.appliedAggregateVersion();
                appliedEpoch = r.appliedEpoch();
            } else {
                // Bước 4b: Thuận &mdash; thu hồi tất cả liên kết của cây.
                RevokeSharingTreeUseCase.Result r = revokeUseCase.execute(
                        new RevokeSharingTreeCommand(operationId, treeId, targetVersion, targetEpoch));
                appliedVersion = r.appliedAggregateVersion();
                appliedEpoch = r.appliedEpoch();
            }

            // Bước 5: Stage phản hồi ACK cho saga.
            stageReply(operationId, "sharing-service", stepCode, "ACK",
                    appliedVersion, appliedEpoch, envelope);
        } catch (RuntimeException ex) {
            // Bước lỗi: ghi log, stage phản hồi FAILED và ném lại để Kafka xử lý retry/DLQ.
            LOG.error("Failed to process delete-tree Saga command stepCode={}", stepCode, ex);
            stageReply(UUID.fromString(envelope.path("operationId").asText()),
                    "sharing-service", stepCode, "FAILED", 0L, 0L, envelope);
            throw ex;
        }
    }

    /**
     * Ghi một bản ghi {@code SagaParticipantReply} vào outbox để saga
     * coordinator nhận được kết quả xử lý của sharing-service.
     *
     * @param operationId   định danh thao tác saga.
     * @param participant   tên participant (cố định: {@code "sharing-service"}).
     * @param stepCode      mã bước saga đã xử lý.
     * @param status        trạng thái phản hồi (ví dụ: {@code ACK}, {@code FAILED}).
     * @param appliedVersion phiên bản aggregate đã áp dụng.
     * @param appliedEpoch  epoch đã áp dụng.
     * @param envelope      bản tin gốc &mdash; dùng để lấy {@code treeId}.
     */
    private void stageReply(UUID operationId, String participant, String stepCode,
                            String status, long appliedVersion, long appliedEpoch,
                            JsonNode envelope) {
        // Trích xuất treeId từ envelope &mdash; vẫn cần để gửi kèm trong reply.
        UUID treeId = UUID.fromString(envelope.path("treeId").asText());

        // Xây dựng payload theo schema SagaParticipantReply.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operationId", operationId.toString());
        payload.put("participantService", participant);
        payload.put("stepCode", stepCode);
        payload.put("status", status);
        payload.put("appliedAggregateVersion", appliedVersion);
        payload.put("appliedEpoch", appliedEpoch);
        payload.put("treeId", treeId.toString());
        // Mã lỗi chỉ đặt khi status = FAILED; ngược lại để null cho rõ ràng.
        payload.put("failureCode", status.equals("FAILED") ? "PARTICIPANT_FAILED" : null);
        payload.put("failureMessage", "");
        payload.put("occurredAtEpochMs", Instant.now().toEpochMilli());
        payload.put("schemaVersion", "v1");

        // Tạo OutboxRecord qua JdbcOutboxWriter.Builder với các header chuẩn.
        JdbcOutboxWriter.Builder b = JdbcOutboxWriter.builder()
                .create("saga-reply", operationId.toString(), 1L,
                        "SagaParticipantReply", 1,
                        "sharing.replies.v1", treeId.toString(), payload);
        b.header("eventType", "SagaParticipantReply");
        b.header("operationId", operationId.toString());
        b.header("treeId", treeId.toString());
        // Stage vào outbox &mdash; sẽ được relay lên Kafka bởi background job.
        outbox.stage(b.build());
    }
}