package com.familya.search.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.familya.platform.outbox.JdbcOutboxWriter;
import com.familya.platform.outbox.OutboxWriter;
import com.familya.search.application.port.in.PurgeSearchTreeCommand;
import com.familya.search.application.port.in.RestoreSearchTreeCommand;
import com.familya.search.application.usecase.PurgeSearchTreeUseCase;
import com.familya.search.application.usecase.RestoreSearchTreeUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka listener lắng nghe các lệnh Saga xoá cây gửi tới {@code tree.commands.v1}.
 *
 * <p>Service search tham gia Saga với hai bước:</p>
 * <ul>
 *   <li>{@code PURGE_SEARCH_TREE} - xoá toàn bộ tài liệu của cây và nâng
 *       watermark (bước chính).</li>
 *   <li>{@code RESTORE_SEARCH_TREE} - khôi phục watermark (bước bù trừ).</li>
 * </ul>
 *
 * <p>Sau khi xử lý, listener ghi một sự kiện phản hồi vào outbox để Saga
 * điều phối có thể tiếp tục. Phản hồi có hai trạng thái: {@code ACK} khi
 * thành công hoặc {@code FAILED} khi xảy ra ngoại lệ.</p>
 */
@Component
public class DeleteTreeSagaCommandListener {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteTreeSagaCommandListener.class);

    private final PurgeSearchTreeUseCase purgeUseCase;
    private final RestoreSearchTreeUseCase restoreUseCase;
    private final OutboxWriter outbox;

    /**
     * Khởi tạo listener với các use case tham gia Saga và writer outbox.
     */
    public DeleteTreeSagaCommandListener(PurgeSearchTreeUseCase purgeUseCase,
                                         RestoreSearchTreeUseCase restoreUseCase,
                                         OutboxWriter outbox) {
        this.purgeUseCase = purgeUseCase;
        this.restoreUseCase = restoreUseCase;
        this.outbox = outbox;
    }

    /**
     * Xử lý một lệnh Saga gửi tới {@code tree.commands.v1}. Chỉ xử lý các
     * bước liên quan tới search service; các bước khác được bỏ qua.
     *
     * @param envelope phong bì JSON chứa thông tin bước Saga.
     */
    @KafkaListener(
            topics = "tree.commands.v1",
            groupId = "search-service.delete-tree",
            containerFactory = "sagaCommandListenerContainerFactory")
    public void onCommand(JsonNode envelope) {
        // Lấy mã bước - nếu không phải PURGE/RESTORE thì bỏ qua ngay.
        String stepCode = envelope.path("stepCode").asText("");
        if (!"PURGE_SEARCH_TREE".equals(stepCode) && !"RESTORE_SEARCH_TREE".equals(stepCode)) {
            return;
        }
        // Bước bù trừ nếu envelope đánh dấu isCompensation hoặc mã bước là RESTORE.
        boolean compensation = envelope.path("isCompensation").asBoolean(false)
                || "RESTORE_SEARCH_TREE".equals(stepCode);
        try {
            // Trích xuất các trường bắt buộc từ envelope.
            UUID operationId = UUID.fromString(envelope.path("operationId").asText());
            UUID treeId = UUID.fromString(envelope.path("treeId").asText());
            long targetVersion = envelope.path("targetAggregateVersion").asLong();
            long targetEpoch = envelope.path("targetEpoch").asLong();
            long appliedVersion; long appliedEpoch;
            if (compensation) {
                // Chạy bước khôi phục (không cần phiên bản/epoch mục tiêu).
                RestoreSearchTreeUseCase.Result r = restoreUseCase.execute(
                        new RestoreSearchTreeCommand(operationId, treeId));
                appliedVersion = r.appliedAggregateVersion();
                appliedEpoch = r.appliedEpoch();
            } else {
                // Chạy bước xoá chính với phiên bản/epoch mục tiêu.
                PurgeSearchTreeUseCase.Result r = purgeUseCase.execute(
                        new PurgeSearchTreeCommand(operationId, treeId, targetVersion, targetEpoch));
                appliedVersion = r.appliedAggregateVersion();
                appliedEpoch = r.appliedEpoch();
            }
            // Phản hồi ACK cho Saga điều phối.
            stageReply(operationId, "search-service", stepCode, "ACK",
                    appliedVersion, appliedEpoch, envelope);
        } catch (RuntimeException ex) {
            // Lỗi: ghi nhận FAILED trước khi ném lại để Saga biết cần xử lý.
            LOG.error("Failed to process delete-tree Saga command stepCode={}", stepCode, ex);
            stageReply(UUID.fromString(envelope.path("operationId").asText()),
                    "search-service", stepCode, "FAILED", 0L, 0L, envelope);
            throw ex;
        }
    }

    /**
     * Ghi phản hồi Saga vào outbox.
     *
     * @param operationId    định danh thao tác Saga.
     * @param participant    tên service tham gia (ở đây là {@code "search-service"}).
     * @param stepCode       mã bước Saga.
     * @param status         trạng thái: {@code "ACK"} hoặc {@code "FAILED"}.
     * @param appliedVersion phiên bản aggregate đã áp dụng.
     * @param appliedEpoch   epoch đã áp dụng.
     * @param envelope       phong bì gốc (dùng để lấy {@code treeId}).
     */
    private void stageReply(UUID operationId, String participant, String stepCode,
                            String status, long appliedVersion, long appliedEpoch,
                            JsonNode envelope) {
        UUID treeId = UUID.fromString(envelope.path("treeId").asText());
        // Payload được giữ ở dạng LinkedHashMap để giữ thứ tự khoá - thuận
        // tiện cho việc debug/log.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operationId", operationId.toString());
        payload.put("participantService", participant);
        payload.put("stepCode", stepCode);
        payload.put("status", status);
        payload.put("appliedAggregateVersion", appliedVersion);
        payload.put("appliedEpoch", appliedEpoch);
        payload.put("treeId", treeId.toString());
        // failureCode chỉ có giá trị khi trạng thái là FAILED; null khi ACK.
        payload.put("failureCode", status.equals("FAILED") ? "PARTICIPANT_FAILED" : null);
        payload.put("failureMessage", "");
        payload.put("occurredAtEpochMs", Instant.now().toEpochMilli());
        payload.put("schemaVersion", "v1");
        JdbcOutboxWriter.Builder b = JdbcOutboxWriter.builder()
                .create("saga-reply", operationId.toString(), 1L,
                        "SagaParticipantReply", 1,
                        "search.replies.v1", treeId.toString(), payload);
        b.header("eventType", "SagaParticipantReply");
        b.header("operationId", operationId.toString());
        b.header("treeId", treeId.toString());
        // Stage vào outbox - publisher của nền tảng sẽ lo chuyển sang Kafka.
        outbox.stage(b.build());
    }
}