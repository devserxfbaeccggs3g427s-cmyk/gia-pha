package com.familya.member.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.familya.member.application.port.in.PurgeMemberTreeCommand;
import com.familya.member.application.port.in.RestoreMemberTreeCommand;
import com.familya.member.application.usecase.PurgeMemberTreeUseCase;
import com.familya.member.application.usecase.RestoreMemberTreeUseCase;
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
 * Kafka listener cho các lệnh đến từ Saga xóa cây gia phả do dịch vụ Tree Access sở hữu.
 * <p>Member Service tham gia Saga này với tư cách participant, đảm nhận hai bước:
 * <ul>
 *   <li>{@code PURGE_MEMBER_TREE}: đánh dấu toàn bộ thành viên trong cây là tombstone.</li>
 *   <li>{@code RESTORE_MEMBER_TREE}: khôi phục các thành viên đã tombstone khi Saga rollback.</li>
 * </ul>
 * <p>Sau khi xử lý, listener sẽ stage reply vào outbox cục bộ để platform publisher đẩy
 * lên topic {@code member.replies.v1}.
 *
 * <p>Đây là bean {@code @Component} thuộc tầng adapter-in trong kiến trúc Hexagonal.
 */
@Component
public class DeleteTreeSagaCommandListener {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteTreeSagaCommandListener.class);

    private final PurgeMemberTreeUseCase purgeUseCase;
    private final RestoreMemberTreeUseCase restoreUseCase;
    private final OutboxWriter outbox;

    /**
     * Khởi tạo listener với các use case tham gia Saga và {@link OutboxWriter} để stage reply.
     *
     * @param purgeUseCase   use case thực hiện bước purge
     * @param restoreUseCase use case thực hiện bước khôi phục
     * @param outbox         writer outbox dùng để stage reply
     */
    public DeleteTreeSagaCommandListener(PurgeMemberTreeUseCase purgeUseCase,
                                         RestoreMemberTreeUseCase restoreUseCase,
                                         OutboxWriter outbox) {
        this.purgeUseCase = purgeUseCase;
        this.restoreUseCase = restoreUseCase;
        this.outbox = outbox;
    }

    /**
     * Xử lý một lệnh Saga nhận được từ topic {@code tree.commands.v1}.
     *
     * <p>Quy tắc xử lý:
     * <ul>
     *   <li>Bỏ qua nếu stepCode không phải {@code PURGE_MEMBER_TREE} hoặc {@code RESTORE_MEMBER_TREE}.</li>
     *   <li>Nếu cờ {@code isCompensation} được bật hoặc stepCode là {@code RESTORE_MEMBER_TREE},
     *       thực hiện khôi phục; ngược lại thực hiện purge.</li>
     *   <li>Stage reply ACK về outbox khi thành công, hoặc FAILED khi có ngoại lệ.</li>
     * </ul>
     *
     * @param envelope phong bì JSON chứa stepCode, operationId, treeId, target version/epoch
     */
    @KafkaListener(
            topics = "tree.commands.v1",
            groupId = "member-service.delete-tree",
            containerFactory = "sagaCommandListenerContainerFactory")
    public void onCommand(JsonNode envelope) {
        // Trích xuất stepCode; chỉ xử lý hai bước liên quan đến Member, các bước khác bỏ qua
        String stepCode = envelope.path("stepCode").asText("");
        if (!"PURGE_MEMBER_TREE".equals(stepCode) && !"RESTORE_MEMBER_TREE".equals(stepCode)) {
            return;
        }
        // Xác định đây là bước compensation hay không: cờ isCompensation được bật, hoặc stepCode là restore
        boolean compensation = envelope.path("isCompensation").asBoolean(false)
                || "RESTORE_MEMBER_TREE".equals(stepCode);
        try {
            UUID operationId = UUID.fromString(envelope.path("operationId").asText());
            UUID treeId = UUID.fromString(envelope.path("treeId").asText());
            long targetVersion = envelope.path("targetAggregateVersion").asLong();
            long targetEpoch = envelope.path("targetEpoch").asLong();

            long appliedVersion;
            long appliedEpoch;
            if (compensation) {
                // Compensation: khôi phục toàn bộ thành viên đã tombstone trong cây
                RestoreMemberTreeUseCase.Result r = restoreUseCase.execute(
                        new RestoreMemberTreeCommand(operationId, treeId));
                appliedVersion = r.appliedAggregateVersion();
                appliedEpoch = r.appliedEpoch();
            } else {
                // Forward: bulk tombstone toàn bộ thành viên đang hoạt động
                PurgeMemberTreeUseCase.Result r = purgeUseCase.execute(
                        new PurgeMemberTreeCommand(operationId, treeId, targetVersion, targetEpoch));
                appliedVersion = r.appliedAggregateVersion();
                appliedEpoch = r.appliedEpoch();
            }
            // Stage reply ACK để Saga owner biết bước đã hoàn tất
            stageReply(operationId, "member-service", stepCode, "ACK",
                    appliedVersion, appliedEpoch, envelope);
        } catch (RuntimeException ex) {
            // Bất kỳ lỗi nào cũng phải trả về FAILED để Saga owner xử lý rollback
            LOG.error("Failed to process delete-tree Saga command stepCode={}", stepCode, ex);
            stageReply(UUID.fromString(envelope.path("operationId").asText()),
                    "member-service", stepCode, "FAILED", 0L, 0L, envelope);
            throw ex;
        }
    }

    /**
     * Stage một reply Saga vào outbox cục bộ. Platform publisher sẽ đọc outbox và phát
     * lên topic {@code member.replies.v1} để Saga owner nhận.
     *
     * @param operationId    mã operationId của Saga
     * @param participant    tên dịch vụ tham gia (cố định là {@code member-service})
     * @param stepCode       mã bước đã xử lý
     * @param status         trạng thái ({@code ACK} hoặc {@code FAILED})
     * @param appliedVersion phiên bản aggregate đã áp dụng
     * @param appliedEpoch   epoch đã áp dụng
     * @param envelope       phong bì gốc để lấy treeId
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
        payload.put("failureCode", status.equals("FAILED") ? "PARTICIPANT_FAILED" : null);
        payload.put("failureMessage", "");
        payload.put("occurredAtEpochMs", Instant.now().toEpochMilli());
        payload.put("schemaVersion", "v1");
        JdbcOutboxWriter.Builder b = JdbcOutboxWriter.builder()
                .create("saga-reply", operationId.toString(), 1L,
                        "SagaParticipantReply", 1,
                        "member.replies.v1", treeId.toString(), payload);
        b.header("eventType", "SagaParticipantReply");
        b.header("operationId", operationId.toString());
        b.header("treeId", treeId.toString());
        outbox.stage(b.build());
    }
}