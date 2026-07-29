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

@Component
public class DeleteMemberSagaCommandListener {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteMemberSagaCommandListener.class);

    private final AdvanceDeleteMemberRevisionUseCase useCase;
    private final SagaReplyStager reply;

    public DeleteMemberSagaCommandListener(AdvanceDeleteMemberRevisionUseCase useCase,
                                           SagaReplyStager reply) {
        this.useCase = useCase;
        this.reply = reply;
    }

    @KafkaListener(
            topics = "member.commands.v1",
            groupId = "tree-access-service.delete-member",
            containerFactory = "sagaCommandListenerContainerFactory")
    public void onCommand(JsonNode envelope) {
        try {
            JsonNode step = envelope.path("stepCode");
            if (step.isMissingNode() || !"ADVANCE_DELETE_MEMBER_REV".equals(step.asText())) {
                return;
            }
            UUID operationId = UUID.fromString(envelope.path("operationId").asText());
            UUID treeId = UUID.fromString(envelope.path("treeId").asText());
            long targetVersion = envelope.path("targetAggregateVersion").asLong();
            long targetEpoch = envelope.path("targetEpoch").asLong();

            AdvanceDeleteMemberRevisionUseCase.Result result =
                    useCase.execute(new AdvanceDeleteMemberRevisionCommand(
                            operationId, treeId, 0L, targetVersion, targetEpoch));

            reply.stageAck(operationId, "tree-access-service", "ADVANCE_DELETE_MEMBER_REV",
                    result.appliedAggregateVersion(), result.appliedEpoch(), Instant.now());
        } catch (RuntimeException ex) {
            LOG.error("Failed to process delete-member Saga command", ex);
            reply.stageFailed(envelope, "tree-access-service", "ADVANCE_DELETE_MEMBER_REV",
                    "ADVANCE_FAILED", ex.getMessage());
            throw ex;
        }
    }
}