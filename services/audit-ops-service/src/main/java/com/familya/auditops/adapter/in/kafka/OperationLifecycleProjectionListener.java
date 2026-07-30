package com.familya.auditops.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.familya.auditops.adapter.out.persistence.OperationLifecycleDeadLetterStore;
import com.familya.auditops.application.port.out.OperationLifecycleProjection;
import com.familya.auditops.domain.model.OperationLifecycleRow;
import com.familya.platform.inbox.InboxRecord;
import com.familya.platform.inbox.InboxStore;
import com.familya.platform.telemetry.PlatformMetrics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Consumes {@code operations.events.v1} published by every Saga owner service
 * (Member, Tree Access, ...) and updates the operator projection. Audit Ops
 * is NOT a business participant; it only mirrors lifecycle events for the
 * operator UI and replay.
 */
@Component
public class OperationLifecycleProjectionListener {

    private static final Logger LOG = LoggerFactory.getLogger(OperationLifecycleProjectionListener.class);
    private static final String CONSUMER = "audit-ops-service.lifecycle";

    private final OperationLifecycleProjection projection;
    private final InboxStore inbox;
    private final PlatformMetrics metrics;
    private final OperationLifecycleDeadLetterStore deadLetterStore;

    public OperationLifecycleProjectionListener(OperationLifecycleProjection projection,
                                                InboxStore inbox,
                                                PlatformMetrics metrics,
                                                OperationLifecycleDeadLetterStore deadLetterStore) {
        this.projection = projection;
        this.inbox = inbox;
        this.metrics = metrics;
        this.deadLetterStore = deadLetterStore;
    }

    @KafkaListener(topics = "operations.events.v1", groupId = "${spring.application.name:audit-ops-service}.lifecycle")
    @Transactional
    public void onEvent(ConsumerRecord<String, Object> record) {
        String eventId = headerString(record, "event_id");
        if (eventId == null) {
            deadLetterStore.save(record, new IllegalArgumentException("Missing event_id header"));
            return;
        }
        if (inbox.exists(eventId, CONSUMER)) {
            metrics.consumerDuplicate(CONSUMER, record.topic());
            return;
        }

        JsonNode n;
        try {
            n = parse(record);
        } catch (RuntimeException parseError) {
            LOG.error("Malformed lifecycle event event_id={} offset={}", eventId, record.offset(), parseError);
            deadLetterStore.save(record, parseError);
            inbox.markProcessed(new InboxRecord(
                    eventId, CONSUMER, record.topic(), record.partition(), record.offset(), Instant.now()));
            return;
        }
        if (n == null) {
            IllegalArgumentException error = new IllegalArgumentException("Empty lifecycle event payload");
            deadLetterStore.save(record, error);
            inbox.markProcessed(new InboxRecord(
                    eventId, CONSUMER, record.topic(), record.partition(), record.offset(), Instant.now()));
            return;
        }

        try {
            String eventType = headerString(record, "event_type");
            if (eventType == null) eventType = n.path("eventType").asText("");

            UUID operationId = UUID.fromString(n.path("operationId").asText());
            UUID treeId = n.hasNonNull("treeId") ? UUID.fromString(n.path("treeId").asText()) : null;
            UUID userId = n.hasNonNull("initiatingUserId") ? UUID.fromString(n.path("initiatingUserId").asText()) : null;
            String ownerService = n.path("ownerService").asText("unknown");
            String sagaType = n.path("sagaType").asText("");
            Long targetVersion = n.hasNonNull("targetAggregateVersion") ? n.path("targetAggregateVersion").asLong() : null;
            Long targetEpoch = n.hasNonNull("targetEpoch") ? n.path("targetEpoch").asLong() : null;
            String state = n.path("state").asText("");
            String failureCode = n.hasNonNull("failureCode") ? n.path("failureCode").asText() : null;
            String failureMessage = n.hasNonNull("failureMessage") ? n.path("failureMessage").asText() : null;
            String failureRoutingField = n.hasNonNull("failureRouting") ? n.path("failureRouting").asText() : null;
            Instant startedAt = n.hasNonNull("startedAt")
                    ? Instant.parse(n.path("startedAt").asText())
                    : Instant.now();
            Instant occurredAt = n.hasNonNull("occurredAt")
                    ? Instant.parse(n.path("occurredAt").asText())
                    : Instant.now();

            String routing = failureRoutingField != null ? failureRoutingField : failureRouting(state);
            OperationLifecycleRow row = new OperationLifecycleRow(
                    operationId, ownerService, sagaType, treeId, userId,
                    state, targetVersion, targetEpoch,
                    failureCode, failureMessage, routing, startedAt, occurredAt,
                    isTerminal(state) ? occurredAt : null);

            if ("OperationStarted".equals(eventType) || n.has("startedAt")) {
                projection.upsertStarted(row, eventId);
            } else {
                projection.applyStateChange(row, isTerminal(state) ? occurredAt : null, eventId);
            }
            inbox.markProcessed(new InboxRecord(
                    eventId, CONSUMER, record.topic(), record.partition(), record.offset(), Instant.now()));
            metrics.consumerProcessed(CONSUMER, record.topic());
        } catch (RuntimeException e) {
            LOG.error("Operation lifecycle projection failed event_id={} offset={}",
                    eventId, record.offset(), e);
            throw e;
        }
    }

    private static String failureRouting(String state) {
        return switch (state) {
            case "COMPENSATING" -> "COMPENSATING";
            case "COMPENSATED" -> "COMPENSATED";
            case "MANUAL_REVIEW" -> "MANUAL_REVIEW";
            case "FAILED" -> "FAILED";
            case "DLQ", "DEAD_LETTERED" -> "DLQ";
            default -> null;
        };
    }

    private static boolean isTerminal(String state) {
        return "SUCCEEDED".equals(state) || "FAILED".equals(state)
                || "MANUAL_REVIEW".equals(state) || "CANCELLED".equals(state)
                || "COMPENSATED".equals(state);
    }

    private static JsonNode parse(ConsumerRecord<String, Object> record) {
        Object v = record.value();
        if (v == null) {
            throw new IllegalArgumentException("Null payload");
        }
        String s;
        if (v instanceof String str) {
            s = str;
        } else if (v instanceof byte[] bytes) {
            s = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } else {
            s = v.toString();
        }
        if (s.isBlank()) {
            throw new IllegalArgumentException("Blank payload");
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(s);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed JSON payload", e);
        }
    }

    private static String headerString(ConsumerRecord<?, ?> record, String name) {
        var h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value());
    }
}