package com.familya.member.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.familya.member.application.port.in.DeleteMemberSagaReplyCommand;
import com.familya.member.application.usecase.DeleteMemberSagaReplyProcessor;
import com.familya.platform.inbox.InboxRecord;
import com.familya.platform.inbox.InboxStore;
import com.familya.platform.telemetry.PlatformMetrics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Consumes Saga participant replies for the delete-member Saga. Member
 * Service owns this Saga; replies for unknown operation ids are dropped.
 */
@Component
public class DeleteMemberSagaReplyListener {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteMemberSagaReplyListener.class);
    private static final String CONSUMER = "member-service.delete-member";

    private final DeleteMemberSagaReplyProcessor processor;
    private final InboxStore inbox;
    private final PlatformMetrics metrics;

    public DeleteMemberSagaReplyListener(DeleteMemberSagaReplyProcessor processor,
                                         InboxStore inbox,
                                         PlatformMetrics metrics) {
        this.processor = processor;
        this.inbox = inbox;
        this.metrics = metrics;
    }

    @KafkaListener(
            topics = {
                    "relationship.replies.v1",
                    "event.replies.v1",
                    "media.replies.v1",
                    "tree-access.replies.v1"
            },
            groupId = "${spring.application.name:member-service}.delete-member")
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
            DeleteMemberSagaReplyCommand cmd = parseReply(record);
            if (cmd == null) return;
            processor.process(cmd);
            metrics.consumerProcessed(CONSUMER, record.topic());
        } catch (RuntimeException e) {
            LOG.error("delete-member Saga reply processing failed topic={} offset={}",
                    record.topic(), record.offset(), e);
        }
    }

    private static DeleteMemberSagaReplyCommand parseReply(ConsumerRecord<String, Object> record) {
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
            long appliedVersion = n.hasNonNull("appliedAggregateVersion")
                    ? n.path("appliedAggregateVersion").asLong() : 0L;
            long appliedEpoch = n.hasNonNull("appliedEpoch")
                    ? n.path("appliedEpoch").asLong() : 0L;
            String failureCode = n.hasNonNull("failureCode") ? n.path("failureCode").asText() : null;
            String failureMessage = n.hasNonNull("failureMessage") ? n.path("failureMessage").asText() : null;
            return new DeleteMemberSagaReplyCommand(operationId, participant, stepCode,
                    appliedVersion, appliedEpoch, compensationApplied, failed,
                    failureCode, failureMessage);
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