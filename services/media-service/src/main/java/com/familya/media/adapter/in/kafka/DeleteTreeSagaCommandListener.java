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

@Component
public class DeleteTreeSagaCommandListener {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteTreeSagaCommandListener.class);

    private final PurgeMediaMetadataTreeUseCase purgeUseCase;
    private final RestoreMediaMetadataTreeUseCase restoreUseCase;
    private final OutboxWriter outbox;

    public DeleteTreeSagaCommandListener(PurgeMediaMetadataTreeUseCase purgeUseCase,
                                         RestoreMediaMetadataTreeUseCase restoreUseCase,
                                         OutboxWriter outbox) {
        this.purgeUseCase = purgeUseCase;
        this.restoreUseCase = restoreUseCase;
        this.outbox = outbox;
    }

    @KafkaListener(
            topics = "tree.commands.v1",
            groupId = "media-service.delete-tree",
            containerFactory = "sagaCommandListenerContainerFactory")
    public void onCommand(JsonNode envelope) {
        String stepCode = envelope.path("stepCode").asText("");
        if (!"PURGE_MEDIA_METADATA_TREE".equals(stepCode)
                && !"RESTORE_MEDIA_METADATA_TREE".equals(stepCode)) {
            return;
        }
        boolean compensation = envelope.path("isCompensation").asBoolean(false)
                || "RESTORE_MEDIA_METADATA_TREE".equals(stepCode);
        try {
            UUID operationId = UUID.fromString(envelope.path("operationId").asText());
            UUID treeId = UUID.fromString(envelope.path("treeId").asText());
            long targetVersion = envelope.path("targetAggregateVersion").asLong();
            long targetEpoch = envelope.path("targetEpoch").asLong();
            boolean placeHolds = envelope.path("placeRetentionHolds").asBoolean(true);
            long appliedVersion; long appliedEpoch;
            if (compensation) {
                RestoreMediaMetadataTreeUseCase.Result r = restoreUseCase.execute(
                        new RestoreMediaMetadataTreeCommand(operationId, treeId));
                appliedVersion = r.appliedAggregateVersion();
                appliedEpoch = r.appliedEpoch();
            } else {
                PurgeMediaMetadataTreeUseCase.Result r = purgeUseCase.execute(
                        new PurgeMediaMetadataTreeCommand(operationId, treeId, placeHolds,
                                targetVersion, targetEpoch));
                appliedVersion = r.appliedAggregateVersion();
                appliedEpoch = r.appliedEpoch();
            }
            stageReply(operationId, "media-service", stepCode, "ACK",
                    appliedVersion, appliedEpoch, envelope);
        } catch (RuntimeException ex) {
            LOG.error("Failed to process delete-tree Saga command stepCode={}", stepCode, ex);
            stageReply(UUID.fromString(envelope.path("operationId").asText()),
                    "media-service", stepCode, "FAILED", 0L, 0L, envelope);
            throw ex;
        }
    }

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
                        "media.replies.v1", treeId.toString(), payload);
        b.header("eventType", "SagaParticipantReply");
        b.header("operationId", operationId.toString());
        b.header("treeId", treeId.toString());
        outbox.stage(b.build());
    }
}