package com.familya.treeaccess.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.familya.treeaccess.application.port.in.AdvanceDeleteMemberRevisionCommand;
import com.familya.treeaccess.application.usecase.AdvanceDeleteMemberRevisionUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka listener lắng nghe lệnh Saga từ member-service trên topic
 * {@code member.commands.v1}. Khi nhận được bước {@code ADVANCE_DELETE_MEMBER_REV},
 * listener sẽ ủy quyền cho {@link AdvanceDeleteMemberRevisionUseCase} để tăng
 * revision/epoch của cây, sau đó stage một phản hồi ACK (hoặc FAILED) lên outbox.
 */
@Component
public class DeleteMemberSagaCommandListener {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteMemberSagaCommandListener.class);

    /** Use-case tăng revision cây theo lệnh của Saga delete-member. */
    private final AdvanceDeleteMemberRevisionUseCase useCase;

    /** Bộ stage phản hồi Saga lên outbox để truyền về member-service. */
    private final SagaReplyStager reply;

    /**
     * Khởi tạo listener với use-case và bộ stage phản hồi.
     *
     * @param useCase use-case xử lý lệnh {@code ADVANCE_DELETE_MEMBER_REV}
     * @param reply    bộ stage reply Saga
     */
    public DeleteMemberSagaCommandListener(AdvanceDeleteMemberRevisionUseCase useCase,
                                           SagaReplyStager reply) {
        this.useCase = useCase;
        this.reply = reply;
    }

    /**
     * Xử lý mỗi message nhận được từ Kafka.
     *
     * <p>Quy trình:</p>
     * <ol>
     *   <li>Lọc ra những bước đúng mã {@code ADVANCE_DELETE_MEMBER_REV}, bỏ qua các lệnh khác
     *       mà service này không phụ trách.</li>
     *   <li>Chuyển các trường JSON thành {@link UUID} và số nguyên (revision/epoch mục tiêu).</li>
     *   <li>Ủy quyền cho use-case để cập nhật revision/epoch trong cùng transaction.</li>
     *   <li>Stage một ACK lên outbox chứa revision/epoch mới đã áp dụng.</li>
     *   <li>Nếu có lỗi runtime, stage một FAILED và ném tiếp để Kafka tiến hành retry/DLQ.</li>
     * </ol>
     *
     * @param envelope bản tin JSON từ topic {@code member.commands.v1}
     */
    @KafkaListener(
            topics = "member.commands.v1",
            groupId = "tree-access-service.delete-member",
            containerFactory = "sagaCommandListenerContainerFactory")
    public void onCommand(JsonNode envelope) {
        try {
            // Chỉ xử lý bước mà service này phụ trách; các bước khác đã có consumer riêng.
            JsonNode step = envelope.path("stepCode");
            if (step.isMissingNode() || !"ADVANCE_DELETE_MEMBER_REV".equals(step.asText())) {
                return;
            }
            UUID operationId = UUID.fromString(envelope.path("operationId").asText());
            UUID treeId = UUID.fromString(envelope.path("treeId").asText());
            long targetVersion = envelope.path("targetAggregateVersion").asLong();
            long targetEpoch = envelope.path("targetEpoch").asLong();

            // Ủy quyền cho use-case; revision/epoch mới đã được lưu trong cùng transaction.
            AdvanceDeleteMemberRevisionUseCase.Result result =
                    useCase.execute(new AdvanceDeleteMemberRevisionCommand(
                            operationId, treeId, 0L, targetVersion, targetEpoch));

            // Stage phản hồi ACK về member-service để Saga biết bước này đã hoàn tất.
            reply.stageAck(operationId, "tree-access-service", "ADVANCE_DELETE_MEMBER_REV",
                    result.appliedAggregateVersion(), result.appliedEpoch(), Instant.now());
        } catch (RuntimeException ex) {
            // Ghi log và đẩy bản tin sang nhánh FAILED; vẫn ném lại để Kafka xử lý retry/DLQ theo chính sách.
            LOG.error("Failed to process delete-member Saga command", ex);
            reply.stageFailed(envelope, "tree-access-service", "ADVANCE_DELETE_MEMBER_REV",
                    "ADVANCE_FAILED", ex.getMessage());
            throw ex;
        }
    }
}