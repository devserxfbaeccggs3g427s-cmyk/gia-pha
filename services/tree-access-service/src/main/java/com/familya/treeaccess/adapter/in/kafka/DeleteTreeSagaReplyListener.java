package com.familya.treeaccess.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.familya.platform.inbox.InboxRecord;
import com.familya.platform.inbox.InboxStore;
import com.familya.platform.telemetry.PlatformMetrics;
import com.familya.treeaccess.application.port.in.DeleteTreeSagaReplyCommand;
import com.familya.treeaccess.application.usecase.DeleteTreeSagaReplyProcessor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
public class DeleteTreeSagaReplyListener {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteTreeSagaReplyListener.class);
    private static final String CONSUMER = "tree-access-service.delete-tree";

    private final DeleteTreeSagaReplyProcessor processor;
    private final InboxStore inbox;
    private final PlatformMetrics metrics;
    private final DeleteTreeSagaDeadLetterStore deadLetterStore;
    private final ObjectMapper json;

    public DeleteTreeSagaReplyListener(DeleteTreeSagaReplyProcessor processor,
                                       InboxStore inbox,
                                       PlatformMetrics metrics,
                                       DeleteTreeSagaDeadLetterStore deadLetterStore,
                                       ObjectMapper json) {
        this.processor = processor;
        this.inbox = inbox;
        this.metrics = metrics;
        this.deadLetterStore = deadLetterStore;
        this.json = json;
    }

    @KafkaListener(
            topics = {
                    "member.replies.v1",
                    "relationship.replies.v1",
                    "event.replies.v1",
                    "media.replies.v1",
                    "sharing.replies.v1",
                    "search.replies.v1"
            },
            groupId = "${spring.application.name:tree-access-service}.delete-tree")
    @Transactional
    public void onReply(ConsumerRecord<String, Object> record) {
        String eventId = headerString(record, "event_id");
        if (eventId == null) {
            eventId = "offset:" + record.topic() + ":" + record.partition() + ":" + record.offset();
        }
        if (inbox.exists(eventId, CONSUMER)) {
            metrics.consumerDuplicate(CONSUMER, record.topic());
            return;
        }

        DeleteTreeSagaReplyCommand cmd;
        try {
            cmd = parseReply(record);
        } catch (RuntimeException parseError) {
            deadLetterStore.savePoison(record, parseError);
            inbox.markProcessed(new InboxRecord(
                    eventId, CONSUMER, record.topic(), record.partition(), record.offset(), Instant.now()));
            return;
        }
        if (cmd == null) {
            deadLetterStore.savePoison(record, new IllegalArgumentException("Empty delete-tree reply"));
            inbox.markProcessed(new InboxRecord(
                    eventId, CONSUMER, record.topic(), record.partition(), record.offset(), Instant.now()));
            return;
        }

        try {
            processor.process(cmd);
        } catch (RuntimeException processError) {
            LOG.error("Saga processor rejected reply op={} step={} reason={}",
                    cmd.operationId(), cmd.stepCode(), processError.toString());
            deadLetterStore.saveRetryExhausted(
                    cmd.operationId(), cmd.participantService(), cmd.stepCode(),
                    -1, processError);
            inbox.markProcessed(new InboxRecord(
                    eventId, CONSUMER, record.topic(), record.partition(), record.offset(), Instant.now()));
            return;
        }
        inbox.markProcessed(new InboxRecord(
                eventId, CONSUMER, record.topic(), record.partition(), record.offset(), Instant.now()));
        metrics.consumerProcessed(CONSUMER, record.topic());
    }

    private DeleteTreeSagaReplyCommand parseReply(ConsumerRecord<String, Object> record) {
        Object v = record.value();
        if (!(v instanceof String s) || s.isBlank()) return null;
        JsonNode n;
        try {
            n = json.readTree(s);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed JSON", e);
        }
        UUID operationId;
        try {
            operationId = UUID.fromString(requiredText(n, "operationId"));
        } catch (IllegalArgumentException illegal) {
            throw illegal;
        }
        String participant = requiredText(n, "participantService");
        String stepCode = requiredText(n, "stepCode");
        String status = requiredText(n, "status");
        boolean compensationReply = stepCode.startsWith("RESTORE_");
        boolean failed = "FAILED".equals(status);
        boolean compensationApplied = compensationReply || "COMPENSATED".equals(status);
        stepCode = forwardStepCode(stepCode);
        long appliedVersion = n.hasNonNull("appliedAggregateVersion")
                ? n.path("appliedAggregateVersion").asLong() : 0L;
        long appliedEpoch = n.hasNonNull("appliedEpoch")
                ? n.path("appliedEpoch").asLong() : 0L;
        String failureCode = n.hasNonNull("failureCode") ? n.path("failureCode").asText() : null;
        String failureMessage = n.hasNonNull("failureMessage") ? n.path("failureMessage").asText() : null;
        return new DeleteTreeSagaReplyCommand(operationId, participant, stepCode,
                appliedVersion, appliedEpoch, compensationApplied, failed,
                failureCode, failureMessage);
    }

    private static String forwardStepCode(String stepCode) {
        return switch (stepCode) {
            case "RESTORE_MEMBER_TREE" -> "PURGE_MEMBER_TREE";
            case "RESTORE_RELATIONSHIP_TREE" -> "PURGE_RELATIONSHIP_TREE";
            case "RESTORE_EVENT_TREE" -> "PURGE_EVENT_TREE";
            case "RESTORE_MEDIA_METADATA_TREE" -> "PURGE_MEDIA_METADATA_TREE";
            case "RESTORE_SHARING_TREE" -> "REVOKE_SHARING_TREE";
            case "RESTORE_SEARCH_TREE" -> "PURGE_SEARCH_TREE";
            case "RESTORE_TREE" -> "TOMBSTONE_TREE";
            default -> stepCode;
        };
    }

    private static String requiredText(JsonNode n, String field) {
        JsonNode child = n.get(field);
        if (child == null || child.isNull()) {
            throw new IllegalArgumentException("Missing required field " + field);
        }
        return child.asText();
    }

    private static String headerString(ConsumerRecord<?, ?> record, String name) {
        var h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value());
    }
}