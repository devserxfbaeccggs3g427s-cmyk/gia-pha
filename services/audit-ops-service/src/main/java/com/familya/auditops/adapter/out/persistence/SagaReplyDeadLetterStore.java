/**
 * Saga-reply DLQ store. Persists malformed replies on {@code saga.replies.v1}
 * (which Audit Ops consumes solely as a projection consumer; no business
 * authority is held here).
 *
 * <p>Stores topic, partition, offset, payload, error, and timestamp as
 * required by Task 13.2. Extracts {@code operationId}, {@code participantService}
 * and {@code stepName} from the redacted payload when available so operators
 * can route replay to the owning service. The key
 * {@code (consumer, topic, partition, offset)} is stable so re-attempts are
 * idempotent.</p>
 */
package com.familya.auditops.adapter.out.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.familya.auditops.application.usecase.PayloadRedactor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class SagaReplyDeadLetterStore {

    private static final Logger LOG = LoggerFactory.getLogger(SagaReplyDeadLetterStore.class);
    static final String CONSUMER = "audit-ops-service.saga-replies";

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;
    private final PayloadRedactor redactor;

    public SagaReplyDeadLetterStore(NamedParameterJdbcTemplate jdbc, ObjectMapper json, PayloadRedactor redactor) {
        this.jdbc = jdbc;
        this.json = json;
        this.redactor = redactor;
    }

    public void savePoison(ConsumerRecord<String, Object> record, Throwable error) {
        Optional<JsonNode> redacted = toJsonNode(record.value()).flatMap(redactor::redact);
        JsonNode raw = toJsonNode(record.value()).orElse(null);
        UUID operationId = raw == null ? null : extractString(raw, "operationId").flatMap(SagaReplyDeadLetterStore::toUuid).orElse(null);
        String participant = raw == null ? null : extractString(raw, "participantService").orElse(null);
        String step = raw == null ? null : extractString(raw, "stepName").orElse(null);
        Integer attempt = raw == null ? null : extractInt(raw, "attempt");
        jdbc.update("""
                INSERT INTO saga_reply_dead_letter
                    (event_id, consumer, topic, partition_no, offset_no, operation_id,
                     participant_service, step_name, attempt_count,
                     error_class, error_message, redacted_payload_json, quarantined_at)
                VALUES (:eid,:consumer,:topic,:part,:off,:op,:ps,:sn,:ac,:ec,:em,:rp,:qa)
                ON DUPLICATE KEY UPDATE
                    operation_id = VALUES(operation_id),
                    participant_service = VALUES(participant_service),
                    step_name = VALUES(step_name),
                    attempt_count = VALUES(attempt_count),
                    error_class = VALUES(error_class),
                    error_message = VALUES(error_message),
                    redacted_payload_json = VALUES(redacted_payload_json),
                    quarantined_at = VALUES(quarantined_at)
                """,
                new MapSqlParameterSource()
                        .addValue("eid", UUID.nameUUIDFromBytes((CONSUMER + "|" + record.topic() + "|"
                                + record.partition() + "|" + record.offset()).getBytes()).toString())
                        .addValue("consumer", CONSUMER)
                        .addValue("topic", record.topic())
                        .addValue("part", record.partition())
                        .addValue("off", record.offset())
                        .addValue("op", operationId == null ? null : operationId.toString())
                        .addValue("ps", participant)
                        .addValue("sn", step)
                        .addValue("ac", attempt)
                        .addValue("ec", error == null ? null : error.getClass().getName())
                        .addValue("em", error == null ? null : truncate(error.getMessage(), 2048))
                        .addValue("rp", redacted.map(n -> n.toString()).orElse(null))
                        .addValue("qa", Timestamp.from(Instant.now())));
    }

    private Optional<JsonNode> toJsonNode(Object value) {
        if (value == null) return Optional.empty();
        String s;
        if (value instanceof String str) {
            s = str;
        } else if (value instanceof byte[] bytes) {
            s = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } else {
            s = String.valueOf(value);
        }
        if (s.isBlank()) return Optional.empty();
        try {
            return Optional.of(json.readTree(s));
        } catch (Exception e) {
            LOG.debug("Cannot parse reply payload as JSON, dropping payload body", e);
            return Optional.empty();
        }
    }

    private static Optional<String> extractString(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) return Optional.empty();
        return Optional.of(node.get(field).asText());
    }

    private static Integer extractInt(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) return null;
        return node.get(field).asInt();
    }

    private static Optional<UUID> toUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
