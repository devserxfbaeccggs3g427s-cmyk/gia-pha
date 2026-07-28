package com.familya.auditops.adapter.in.kafka;

import com.familya.auditops.application.port.in.RecordParticipantReplyCommand;
import com.familya.auditops.application.usecase.SagaOrchestrator;
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
 * Consumes Saga replies from every participant service on the shared
 * {@code saga.replies.v1} topic. The consumer is dedicated to the
 * audit-ops orchestrator and dedupes through the platform inbox.
 * Replies for unknown operations are ignored (the operation is
 * either owned by another orchestrator or has been deleted).
 *
 * <p>The consumer uses {@code @KafkaListener} so Spring Kafka handles
 * the consumer-group offset; manual commit and partition assignment
 * follow the platform starter defaults (Task 4 / ADR-004).</p>
 */
@Component
public class SagaReplyListener {

    private static final Logger LOG = LoggerFactory.getLogger(SagaReplyListener.class);
    private static final String CONSUMER = "audit-ops-service";

    private final SagaOrchestrator orchestrator;
    private final InboxStore inbox;
    private final PlatformMetrics metrics;

    public SagaReplyListener(SagaOrchestrator orchestrator,
                             InboxStore inbox,
                             PlatformMetrics metrics) {
        this.orchestrator = orchestrator;
        this.inbox = inbox;
        this.metrics = metrics;
    }

    @KafkaListener(topics = "saga.replies.v1", groupId = "${spring.application.name:audit-ops-service}")
    public void onReply(ConsumerRecord<String, Object> record) {
        String eventId = headerString(record, "event_id");
        if (eventId == null) {
            LOG.warn("Dropping reply without event_id topic={} offset={}", record.topic(), record.offset());
            return;
        }
        if (inbox.exists(eventId, CONSUMER)) {
            metrics.consumerDuplicate(CONSUMER, record.topic());
            return;
        }
        inbox.markProcessed(new InboxRecord(
                eventId, CONSUMER, record.topic(), record.partition(), record.offset(), Instant.now()));

        try {
            RecordParticipantReplyCommand cmd = parseReply(record);
            if (cmd == null) {
                LOG.warn("Dropping malformed reply event_id={} offset={}", eventId, record.offset());
                return;
            }
            orchestrator.applyReply(cmd);
            metrics.consumerProcessed(CONSUMER, record.topic());
        } catch (RuntimeException e) {
            LOG.error("Saga reply processing failed event_id={} offset={}", eventId, record.offset(), e);
            metrics.consumerDuplicate(CONSUMER, "saga.reply.failed");
        }
    }

    private static RecordParticipantReplyCommand parseReply(ConsumerRecord<String, Object> record) {
        Object value = record.value();
        if (!(value instanceof String s) || s.isBlank()) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode n = new com.fasterxml.jackson.databind.ObjectMapper().readTree(s);
            UUID operationId = UUID.fromString(requiredText(n, "operationId"));
            String participant = requiredText(n, "participantService");
            String step = requiredText(n, "stepName");
            String outcomeStr = requiredText(n, "outcome");
            RecordParticipantReplyCommand.Outcome outcome =
                    RecordParticipantReplyCommand.Outcome.valueOf(outcomeStr);
            Long ackedRevision = n.hasNonNull("ackedRevision") ? n.get("ackedRevision").asLong() : null;
            Long ackedEpoch = n.hasNonNull("ackedEpoch") ? n.get("ackedEpoch").asLong() : null;
            Long expectedVersion = n.hasNonNull("expectedVersion") ? n.get("expectedVersion").asLong() : null;
            String errorCode = n.hasNonNull("errorCode") ? n.get("errorCode").asText() : null;
            String errorMessage = n.hasNonNull("errorMessage") ? n.get("errorMessage").asText() : null;
            String correlationId = headerString(record, "correlation_id");
            String causationId = headerString(record, "causation_id");
            return new RecordParticipantReplyCommand(operationId, participant, step, outcome,
                    ackedRevision, ackedEpoch, expectedVersion,
                    errorCode, errorMessage, correlationId, causationId);
        } catch (Exception e) {
            return null;
        }
    }

    private static String requiredText(com.fasterxml.jackson.databind.JsonNode n, String field) {
        com.fasterxml.jackson.databind.JsonNode child = n.get(field);
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