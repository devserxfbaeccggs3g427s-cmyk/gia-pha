package com.familya.treeaccess.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
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

import java.time.Instant;
import java.util.UUID;

/**
 * Consumes Saga participant replies for the delete-tree Saga. The owner
 * (Tree Access) is the only legitimate consumer; replies for unknown
 * operations are dropped and dedup is enforced via the platform inbox.
 *
 * <p>Participant reply topics follow the catalog convention:
 * {@code <participant-context>.replies.v1} partitioned by {@code treeId}.
 */
@Component
public class DeleteTreeSagaReplyListener {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteTreeSagaReplyListener.class);
    private static final String CONSUMER = "tree-access-service.delete-tree";

    private final DeleteTreeSagaReplyProcessor processor;
    private final InboxStore inbox;
    private final PlatformMetrics metrics;

    public DeleteTreeSagaReplyListener(DeleteTreeSagaReplyProcessor processor,
                                       InboxStore inbox,
                                       PlatformMetrics metrics) {
        this.processor = processor;
        this.inbox = inbox;
        this.metrics = metrics;
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
    public void onReply(ConsumerRecord<String, Object> record) {
        String eventId = headerString(record, "event_id");
        if (eventId == null) {
            eventId = String.valueOf(record.value()).hashCode() + ":" + record.offset();
        }
        if (inbox.exists(eventId, CONSUMER)) {
            metrics.consumerDuplicate(CONSUMER, record.topic());
            return;
        }
        inbox.markProcessed(new InboxRecord(
                eventId, CONSUMER, record.topic(), record.partition(), record.offset(), Instant.now()));

        try {
            DeleteTreeSagaReplyCommand cmd = parseReply(record);
            if (cmd == null) {
                LOG.warn("Dropping malformed delete-tree reply topic={} offset={}",
                        record.topic(), record.offset());
                return;
            }
            processor.process(cmd);
            metrics.consumerProcessed(CONSUMER, record.topic());
        } catch (RuntimeException e) {
            LOG.error("delete-tree Saga reply processing failed topic={} offset={}",
                    record.topic(), record.offset(), e);
        }
    }

    private static DeleteTreeSagaReplyCommand parseReply(ConsumerRecord<String, Object> record) {
        Object v = record.value();
        if (!(v instanceof String s) || s.isBlank()) return null;
        try {
            JsonNode n = new com.fasterxml.jackson.databind.ObjectMapper().readTree(s);
            UUID operationId = UUID.fromString(requiredText(n, "operationId"));
            String participant = requiredText(n, "participantService");
            String stepCode = requiredText(n, "stepCode");
            String status = requiredText(n, "status");
            boolean failed = "FAILED".equals(status);
            boolean compensationApplied = "COMPENSATED".equals(status);
            Long appliedVersion = n.hasNonNull("appliedAggregateVersion")
                    ? n.path("appliedAggregateVersion").asLong() : null;
            Long appliedEpoch = n.hasNonNull("appliedEpoch")
                    ? n.path("appliedEpoch").asLong() : null;
            String failureCode = n.hasNonNull("failureCode") ? n.path("failureCode").asText() : null;
            String failureMessage = n.hasNonNull("failureMessage") ? n.path("failureMessage").asText() : null;
            return new DeleteTreeSagaReplyCommand(operationId, participant, stepCode,
                    appliedVersion == null ? 0L : appliedVersion,
                    appliedEpoch == null ? 0L : appliedEpoch,
                    compensationApplied, failed, failureCode, failureMessage);
        } catch (Exception e) {
            return null;
        }
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