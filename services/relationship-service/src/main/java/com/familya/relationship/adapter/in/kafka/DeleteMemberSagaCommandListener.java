package com.familya.relationship.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.familya.platform.outbox.JdbcOutboxWriter;
import com.familya.platform.outbox.OutboxWriter;
import com.familya.relationship.application.port.in.DisableMemberRelationshipsCommand;
import com.familya.relationship.application.port.in.RestoreMemberRelationshipsCommand;
import com.familya.relationship.application.usecase.DisableMemberRelationshipsUseCase;
import com.familya.relationship.application.usecase.RestoreMemberRelationshipsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class DeleteMemberSagaCommandListener {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteMemberSagaCommandListener.class);

    private final DisableMemberRelationshipsUseCase disableUseCase;
    private final RestoreMemberRelationshipsUseCase restoreUseCase;
    private final OutboxWriter outbox;

    public DeleteMemberSagaCommandListener(DisableMemberRelationshipsUseCase disableUseCase,
                                           RestoreMemberRelationshipsUseCase restoreUseCase,
                                           OutboxWriter outbox) {
        this.disableUseCase = disableUseCase;
        this.restoreUseCase = restoreUseCase;
        this.outbox = outbox;
    }

    @KafkaListener(
            topics = "member.commands.v1",
            groupId = "relationship-service.delete-member",
            containerFactory = "sagaCommandListenerContainerFactory")
    public void onCommand(JsonNode envelope) {
        String stepCode = envelope.path("stepCode").asText("");
        if (!"DISABLE_RELATIONSHIPS".equals(stepCode)
                && !"RESTORE_MEMBER_RELATIONSHIPS".equals(stepCode)) {
            return;
        }
        boolean compensation = envelope.path("isCompensation").asBoolean(false)
                || "RESTORE_MEMBER_RELATIONSHIPS".equals(stepCode);
        try {
            UUID operationId = UUID.fromString(envelope.path("operationId").asText());
            UUID treeId = UUID.fromString(envelope.path("treeId").asText());
            UUID memberId = UUID.fromString(envelope.path("memberId").asText());
            long targetVersion = envelope.path("targetAggregateVersion").asLong();
            long targetEpoch = envelope.path("targetEpoch").asLong();

            long appliedVersion;
            long appliedEpoch;
            if (compensation) {
                RestoreMemberRelationshipsUseCase.Result r = restoreUseCase.execute(
                        new RestoreMemberRelationshipsCommand(operationId, treeId, memberId));
                appliedVersion = r.appliedAggregateVersion();
                appliedEpoch = r.appliedEpoch();
            } else {
                DisableMemberRelationshipsUseCase.Result r = disableUseCase.execute(
                        new DisableMemberRelationshipsCommand(operationId, treeId, memberId,
                                0L, 0L, targetVersion, targetEpoch));
                appliedVersion = r.appliedAggregateVersion();
                appliedEpoch = r.appliedEpoch();
            }
            stageReply(operationId, "relationship-service", stepCode, "ACK",
                    appliedVersion, appliedEpoch, null, null, envelope);
        } catch (RuntimeException ex) {
            LOG.error("Failed to process delete-member Saga command stepCode={}", stepCode, ex);
            stageReply(UUID.fromString(envelope.path("operationId").asText()),
                    "relationship-service", stepCode, "FAILED", 0L, 0L,
                    "PARTICIPANT_FAILED", ex.getMessage(), envelope);
            if (!compensation) throw ex;
        }
    }

    private void stageReply(UUID operationId, String participant, String stepCode,
                            String status, long appliedVersion, long appliedEpoch,
                            String failureCode, String failureMessage,
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
        payload.put("failureCode", failureCode);
        payload.put("failureMessage", failureMessage == null ? "" : failureMessage);
        payload.put("occurredAtEpochMs", Instant.now().toEpochMilli());
        payload.put("schemaVersion", "v1");
        JdbcOutboxWriter.Builder b = JdbcOutboxWriter.builder()
                .create("saga-reply", operationId.toString(), 1L,
                        "SagaParticipantReply", 1,
                        "relationship.replies.v1", treeId.toString(), payload);
        b.header("eventType", "SagaParticipantReply");
        b.header("operationId", operationId.toString());
        b.header("treeId", treeId.toString());
        outbox.stage(b.build());
    }
}