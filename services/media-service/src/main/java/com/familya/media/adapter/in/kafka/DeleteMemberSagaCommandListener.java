package com.familya.media.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.familya.media.application.port.in.DetachMemberMediaReferencesCommand;
import com.familya.media.application.port.in.RestoreMemberMediaReferencesCommand;
import com.familya.media.application.usecase.DetachMemberMediaReferencesUseCase;
import com.familya.media.application.usecase.RestoreMemberMediaReferencesUseCase;
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
 * Adapter Kafka đầu vào (inbound) — listener tham gia Saga "delete-member".
 * <p>
 * Lắng nghe các lệnh điều phối (saga command) trên topic {@code member.commands.v1}
 * liên quan đến media: {@code DETACH_MEDIA_REFERENCES} và
 * {@code RESTORE_MEMBER_MEDIA_REFERENCES} (compensation). Khi nhận lệnh, thực thi
 * use case tương ứng và stage một {@code SagaParticipantReply} vào outbox để
 * orchestrator saga tiêu thụ và ra quyết định tiếp theo.
 * <p>
 * Hợp đồng thông điệp (envelope JSON):
 * <ul>
 *   <li>{@code operationId} (UUID)</li>
 *   <li>{@code treeId} (UUID)</li>
 *   <li>{@code memberId} (UUID)</li>
 *   <li>{@code targetAggregateVersion} (long)</li>
 *   <li>{@code targetEpoch} (long)</li>
 *   <li>{@code isCompensation} (boolean, mặc định false)</li>
 *   <li>{@code stepCode} — chỉ xử lý 2 giá trị nêu trên, các bước khác bị bỏ qua.</li>
 * </ul>
 * Idempotency: các use case downstream đảm bảo dựa trên {@code operationId}; listener
 * chỉ chuyển tiếp và phản hồi ACK/FAILED. Lỗi RuntimeException ném lại để container
 * Kafka retry theo cấu hình.
 */
@Component
public class DeleteMemberSagaCommandListener {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteMemberSagaCommandListener.class);

    private final DetachMemberMediaReferencesUseCase detachUseCase;
    private final RestoreMemberMediaReferencesUseCase restoreUseCase;
    private final OutboxWriter outbox;

    /**
     * Khởi tạo listener với các use case tham gia saga và writer outbox.
     *
     * @param detachUseCase  use case tách tham chiếu media khỏi member (bước tiến).
     * @param restoreUseCase use case khôi phục tham chiếu media (bước bù trừ).
     * @param outbox         writer outbox JDBC để stage {@code SagaParticipantReply}.
     */
    public DeleteMemberSagaCommandListener(DetachMemberMediaReferencesUseCase detachUseCase,
                                           RestoreMemberMediaReferencesUseCase restoreUseCase,
                                           OutboxWriter outbox) {
        this.detachUseCase = detachUseCase;
        this.restoreUseCase = restoreUseCase;
        this.outbox = outbox;
    }

    /**
     * Điểm vào Kafka — xử lý một envelope lệnh saga.
     * <p>
     * Các bước:
     * <ol>
     *   <li>Lọc theo {@code stepCode}; bỏ qua nếu không thuộc 2 step media-service phụ trách.</li>
     *   <li>Phân tách các trường bắt buộc trong envelope.</li>
     *   <li>Quyết định chiều thực thi: tiến (detach) hay bù trừ (restore).
     *       Bù trừ được kích hoạt nếu cờ {@code isCompensation}=true hoặc stepCode là restore.</li>
     *   <li>Thực thi use case tương ứng; trả ACK kèm {@code appliedAggregateVersion}/{@code appliedEpoch}.</li>
     *   <li>Stage reply thất bại (FAILED) và ném lại exception để container retry.</li>
     * </ol>
     *
     * @param envelope payload JSON lệnh saga từ topic {@code member.commands.v1}.
     * @throws RuntimeException nếu xử lý thất bại — kích hoạt retry/DLQ theo cấu hình Kafka.
     */
    @KafkaListener(
            topics = "member.commands.v1",
            groupId = "media-service.delete-member",
            containerFactory = "sagaCommandListenerContainerFactory")
    public void onCommand(JsonNode envelope) {
        String stepCode = envelope.path("stepCode").asText("");
        // Bộ lọc step: chỉ xử lý các bước media-service phụ trách, các step khác bỏ qua im lặng.
        if (!"DETACH_MEDIA_REFERENCES".equals(stepCode)
                && !"RESTORE_MEMBER_MEDIA_REFERENCES".equals(stepCode)) {
            return;
        }
        try {
            UUID operationId = UUID.fromString(envelope.path("operationId").asText());
            UUID treeId = UUID.fromString(envelope.path("treeId").asText());
            UUID memberId = UUID.fromString(envelope.path("memberId").asText());
            long targetVersion = envelope.path("targetAggregateVersion").asLong();
            long targetEpoch = envelope.path("targetEpoch").asLong();
            // Compensation khi orchestrator đánh dấu rollback HOẶC stepCode là bước restore.
            boolean compensation = envelope.path("isCompensation").asBoolean(false)
                    || "RESTORE_MEMBER_MEDIA_REFERENCES".equals(stepCode);

            long appliedVersion;
            long appliedEpoch;
            if (compensation) {
                // Đường bù trừ: khôi phục tham chiếu đã detach trước đó.
                RestoreMemberMediaReferencesUseCase.Result r = restoreUseCase.execute(
                        new RestoreMemberMediaReferencesCommand(operationId, treeId, memberId));
                appliedVersion = r.appliedAggregateVersion();
                appliedEpoch = r.appliedEpoch();
            } else {
                // Đường tiến: tách mọi tham chiếu media khỏi member sắp bị xóa.
                DetachMemberMediaReferencesUseCase.Result r = detachUseCase.execute(
                        new DetachMemberMediaReferencesCommand(operationId, treeId, memberId,
                                targetVersion, targetEpoch));
                appliedVersion = r.appliedAggregateVersion();
                appliedEpoch = r.appliedEpoch();
            }
            // Gửi ACK về orchestrator qua outbox (sẽ được relay sang topic replies).
            stageReply(operationId, "media-service", stepCode, "ACK",
                    appliedVersion, appliedEpoch, null, null, envelope);
        } catch (RuntimeException ex) {
            // Ghi log + stage reply FAILED trước khi ném lại để Kafka retry/DLQ xử lý.
            LOG.error("Failed to process delete-member Saga command stepCode={}", stepCode, ex);
            stageReply(UUID.fromString(envelope.path("operationId").asText()),
                    "media-service", stepCode, "FAILED", 0L, 0L,
                    "PARTICIPANT_FAILED", ex.getMessage(), envelope);
            throw ex;
        }
    }

    /**
     * Stage một bản tin reply vào outbox để relay về orchestrator saga.
     * <p>
     * Phương thức này chỉ INSERT vào bảng outbox trong cùng transaction với use case
     * (do caller quản lý), đảm bảo reply được gửi đi cùng thành công/thất bại nghiệp vụ.
     *
     * @param operationId    UUID operation do orchestrator cấp.
     * @param participant    tên participant ("media-service").
     * @param stepCode       step code tương ứng trong saga.
     * @param status         "ACK" hoặc "FAILED".
     * @param appliedVersion phiên bản aggregate đã áp dụng (0 nếu thất bại).
     * @param appliedEpoch   epoch áp dụng (0 nếu thất bại).
     * @param failureCode    mã lỗi (ví dụ "PARTICIPANT_FAILED"), null khi ACK.
     * @param failureMessage thông điệp lỗi, null/rỗng khi ACK.
     * @param envelope       envelope gốc để trích xuất treeId.
     */
    private void stageReply(UUID operationId, String participant, String stepCode,
                            String status, long appliedVersion, long appliedEpoch,
                            String failureCode, String failureMessage,
                            JsonNode envelope) {
        UUID treeId = UUID.fromString(envelope.path("treeId").asText());
        // payload tuân thủ schema SagaParticipantReply v1.
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
        // Partition key = treeId để đảm bảo thứ tự xử lý theo cây.
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