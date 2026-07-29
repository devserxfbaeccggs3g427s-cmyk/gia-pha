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

@Component
public class DeleteMemberSagaCommandListener {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteMemberSagaCommandListener.class);

    private final DetachMemberMediaReferencesUseCase detachUseCase;
    private final RestoreMemberMediaReferencesUseCase restoreUseCase;
    private final OutboxWriter outbox;

    public DeleteMemberSagaCommandListener(DetachMemberMediaReferencesUseCase detachUseCase,
                                           RestoreMemberMediaReferencesUseCase restoreUseCase,
                                           OutboxWriter outbox) {
        this.detachUseCase = detachUseCase;
        this.restoreUseCase = restoreUseCase;
        this.outbox = outbox;
    }

    @KafkaListener(
            topics = "member.commands.v1",
            groupId = "media-service.delete-member",
            containerFactory = "sagaCommandListenerContainerFactory")
    public void onCommand(JsonNode envelope) {
        String stepCode = envelope.path("stepCode").asText("");
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
            boolean compensation = envelope.path("isCompensation").asBoolean(false)
                    || "RESTORE_MEMBER_MEDIA_REFERENCES".equals(stepCode);

            long appliedVersion;
            long appliedEpoch;
            if (compensation) {
                RestoreMemberMediaReferencesUseCase.Result r = restoreUseCase.execute(
                        new RestoreMemberMediaReferencesCommand(operationId, treeId, memberId));
                appliedVersion = r.appliedAggregateVersion();
                appliedEpoch = r.appliedEpoch();
            } else {
                DetachMemberMediaReferencesUseCase.Result r = detachUseCase.execute(
                        new DetachMemberMediaReferencesCommand(operationId, treeId, memberId,
                                targetVersion, targetEpoch));
                appliedVersion = r.appliedAggregateVersion();
                appliedEpoch = r.appliedEpoch();
            }
            stageReply(operationId, "media-service", stepCode, "ACK",
                    appliedVersion, appliedEpoch, null, null, envelope);
        } catch (RuntimeException ex) {
            LOG.error("Failed to process delete-member Saga command stepCode={}", stepCode, ex);
            stageReply(UUID.fromString(envelope.path("operationId").asText()),
                    "media-service", stepCode, "FAILED", 0L, 0L,
                    "PARTICIPANT_FAILED", ex.getMessage(), envelope);
            throw ex;
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
                        "media.replies.v1", treeId.toString(), payload);
        b.header("eventType", "SagaParticipantReply");
        b.header("operationId", operationId.toString());
        b.header("treeId", treeId.toString());
        outbox.stage(b.build());
    }
}