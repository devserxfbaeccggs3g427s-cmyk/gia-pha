package com.familya.event.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.familya.event.application.port.in.DetachMemberEventReferencesCommand;
import com.familya.event.application.port.in.RestoreMemberEventReferencesCommand;
import com.familya.event.application.usecase.DetachMemberEventReferencesUseCase;
import com.familya.event.application.usecase.RestoreMemberEventReferencesUseCase;
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
 * Kafka listener cho các lệnh của <b>delete-member Saga</b> trong
 * event-service.
 *
 * <p>Lắng nghe trên topic {@code member.commands.v1} với group id
 * {@code event-service.delete-member} và container factory
 * {@code sagaCommandListenerContainerFactory} (cấu hình ở
 * {@code application.yml}).
 *
 * <h2>Các bước Saga xử lý</h2>
 * <ul>
 *   <li>{@code DETACH_EVENT_REFERENCES}: gọi
 *       {@link DetachMemberEventReferencesUseCase} để gỡ tham chiếu.</li>
 *   <li>{@code RESTORE_MEMBER_EVENT_REFERENCES}: gọi
 *       {@link RestoreMemberEventReferencesUseCase} để khôi phục (khi
 *       Saga rollback).</li>
 * </ul>
 *
 * <p>Các lệnh không thuộc event-service sẽ bị bỏ qua (return sớm).
 *
 * <h2>Phản hồi (SagaReply)</h2>
 * <p>Mọi lệnh xử lý đều phát một bản ghi vào bảng {@code saga-reply} qua
 * outbox (xem {@link SagaReplyStager#stage}). Khi xử lý lỗi, một
 * {@code SagaParticipantReply} với {@code status=FAILED} được ghi.
 *
 * <h2>Idempotency</h2>
 * <p>Listener đảm bảo idempotent ở tầng application (qua các use case
 * detach/restore); vì vậy việc nhận lại cùng một lệnh do retry Kafka sẽ
 * không tạo trạng thái bất thường.
 *
 * @author gia-pha platform
 */
@Component
public class DeleteMemberSagaCommandListener {

    /** Logger dùng cho audit. */
    private static final Logger LOG = LoggerFactory.getLogger(DeleteMemberSagaCommandListener.class);

    private final DetachMemberEventReferencesUseCase detachUseCase;
    private final RestoreMemberEventReferencesUseCase restoreUseCase;
    private final OutboxWriter outbox;

    /**
     * Khởi tạo listener.
     *
     * @param detachUseCase  use case gỡ tham chiếu.
     * @param restoreUseCase use case khôi phục tham chiếu.
     * @param outbox         writer ghi vào bảng outbox (cho phản hồi Saga).
     */
    public DeleteMemberSagaCommandListener(DetachMemberEventReferencesUseCase detachUseCase,
                                           RestoreMemberEventReferencesUseCase restoreUseCase,
                                           OutboxWriter outbox) {
        this.detachUseCase = detachUseCase;
        this.restoreUseCase = restoreUseCase;
        this.outbox = outbox;
    }

    /**
     * Xử lý một envelope lệnh Saga. Sơ đồ xử lý:
     *
     * <ol>
     *   <li>Đọc {@code stepCode} từ envelope; bỏ qua nếu không thuộc
     *       hai bước mà event-service quan tâm.</li>
     *   <li>Parse các trường bắt buộc: {@code operationId}, {@code treeId},
     *       {@code memberId}, {@code targetAggregateVersion},
     *       {@code targetEpoch}.</li>
     *   <li>Xác định compensation: nếu {@code stepCode} là
     *       {@code RESTORE_*} hoặc envelope mang cờ
     *       {@code isCompensation=true} → chuyển sang use case khôi phục.</li>
     *   <li>Thực thi use case tương ứng và stage một
     *       {@code SagaParticipantReply} với {@code status=ACK}.</li>
     *   <li>Nếu có lỗi runtime, stage {@code FAILED} trước khi ném lại
     *       để Kafka có thể retry/replay.</li>
     * </ol>
     *
     * @param envelope JSON envelope từ Kafka.
     */
    @KafkaListener(
            topics = "member.commands.v1",
            groupId = "event-service.delete-member",
            containerFactory = "sagaCommandListenerContainerFactory")
    public void onCommand(JsonNode envelope) {
        // Bước 1: đọc stepCode để lọc sớm các lệnh không thuộc event-service.
        String stepCode = envelope.path("stepCode").asText("");
        if (!"DETACH_EVENT_REFERENCES".equals(stepCode)
                && !"RESTORE_MEMBER_EVENT_REFERENCES".equals(stepCode)) {
            return;
        }

        try {
            // Bước 2: parse các trường bắt buộc từ envelope.
            UUID operationId = UUID.fromString(envelope.path("operationId").asText());
            UUID treeId = UUID.fromString(envelope.path("treeId").asText());
            UUID memberId = UUID.fromString(envelope.path("memberId").asText());
            long targetVersion = envelope.path("targetAggregateVersion").asLong();
            long targetEpoch = envelope.path("targetEpoch").asLong();

            // Bước 3: xác định compensation — Saga có thể đánh dấu qua cờ
            // isCompensation hoặc sử dụng stepCode riêng cho compensation.
            boolean compensation = envelope.path("isCompensation").asBoolean(false)
                    || "RESTORE_MEMBER_EVENT_REFERENCES".equals(stepCode);

            long appliedVersion;
            long appliedEpoch;
            if (compensation) {
                // Bước 4a: gọi use case restore.
                RestoreMemberEventReferencesUseCase.Result r = restoreUseCase.execute(
                        new RestoreMemberEventReferencesCommand(operationId, treeId, memberId));
                appliedVersion = r.appliedAggregateVersion();
                appliedEpoch = r.appliedEpoch();
            } else {
                // Bước 4b: gọi use case detach chính.
                DetachMemberEventReferencesUseCase.Result r = detachUseCase.execute(
                        new DetachMemberEventReferencesCommand(operationId, treeId, memberId,
                                targetVersion, targetEpoch));
                appliedVersion = r.appliedAggregateVersion();
                appliedEpoch = r.appliedEpoch();
            }

            // Bước 5: stage phản hồi ACK.
            stageReply(operationId, "event-service", stepCode, "ACK",
                    appliedVersion, appliedEpoch, null, null, envelope);
        } catch (RuntimeException ex) {
            // Bước 6: ghi log lỗi, stage phản hồi FAILED, rồi ném lại để
            // Kafka retry theo cơ chế container factory.
            LOG.error("Failed to process delete-member Saga command stepCode={}", stepCode, ex);
            stageReply(UUID.fromString(envelope.path("operationId").asText()),
                    "event-service", stepCode, "FAILED", 0L, 0L,
                    "PARTICIPANT_FAILED", ex.getMessage(), envelope);
            throw ex;
        }
    }

    /**
     * Stage một bản ghi phản hồi Saga vào outbox.
     *
     * @param operationId     định danh Saga.
     * @param participant     tên participant (ở đây cố định {@code "event-service"}).
     * @param stepCode        mã bước Saga.
     * @param status          trạng thái phản hồi: {@code ACK} hoặc {@code FAILED}.
     * @param appliedVersion  phiên bản đã áp dụng.
     * @param appliedEpoch    epoch đã áp dụng.
     * @param failureCode     mã lỗi hoặc {@code null}.
     * @param failureMessage  thông điệp lỗi hoặc {@code null}.
     * @param envelope        envelope gốc (để đọc {@code treeId}).
     */
    private void stageReply(UUID operationId, String participant, String stepCode,
                            String status, long appliedVersion, long appliedEpoch,
                            String failureCode, String failureMessage,
                            JsonNode envelope) {
        // Trích treeId từ envelope để gắn làm partition key cho reply.
        UUID treeId = UUID.fromString(envelope.path("treeId").asText());

        // Payload dùng LinkedHashMap để bảo toàn thứ tự khi serialize JSON.
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

        // Tạo builder, gắn headers rồi stage vào outbox.
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
