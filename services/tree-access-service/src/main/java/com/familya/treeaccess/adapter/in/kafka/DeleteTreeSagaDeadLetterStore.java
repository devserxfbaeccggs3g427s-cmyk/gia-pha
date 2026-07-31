package com.familya.treeaccess.adapter.in.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class DeleteTreeSagaDeadLetterStore {
    static final String CONSUMER = "tree-access-service.delete-tree";
    static final String SAGA_TYPE = "delete-tree";
    static final String SOURCE_KIND_POISON = "POISON";
    static final String SOURCE_KIND_RETRY_EXHAUSTED = "RETRY_EXHAUSTED";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public DeleteTreeSagaDeadLetterStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void savePoison(ConsumerRecord<String, Object> record, Throwable error) {
        String sourceKey = record.topic() + ":" + record.partition() + ":" + record.offset();
        insert(SOURCE_KIND_POISON, sourceKey, null, null, null, null,
                record, record.value(), error);
    }

    public void saveRetryExhausted(UUID operationId, String participantService, String stepCode,
                                   int attemptCount, Throwable error) {
        String sourceKey = operationId + "|" + participantService + "|" + stepCode + "|" + attemptCount;
        insert(SOURCE_KIND_RETRY_EXHAUSTED, sourceKey,
                operationId, participantService, stepCode, attemptCount,
                null, null, error);
    }

    private void insert(String sourceKind, String sourceKey,
                        UUID operationId, String participantService, String stepCode,
                        Integer attemptCount,
                        ConsumerRecord<String, Object> record,
                        Object payload, Throwable error) {
        jdbc.update("""
                INSERT INTO tree_saga_dead_letter
                    (id, consumer, source_kind, source_key,
                     operation_id, saga_type, participant_service, step_code,
                     attempt_count, topic, partition_id, offset_value, payload_text,
                     error_class, error_message, occurred_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE
                    operation_id = VALUES(operation_id),
                    saga_type = VALUES(saga_type),
                    participant_service = VALUES(participant_service),
                    step_code = VALUES(step_code),
                    attempt_count = VALUES(attempt_count),
                    payload_text = VALUES(payload_text),
                    error_class = VALUES(error_class),
                    error_message = VALUES(error_message),
                    occurred_at = VALUES(occurred_at)
                """,
                UUID.randomUUID().toString(), CONSUMER, sourceKind, sourceKey,
                operationId == null ? null : operationId.toString(), SAGA_TYPE,
                participantService, stepCode, attemptCount,
                record == null ? "" : record.topic(),
                record == null ? -1 : record.partition(),
                record == null ? -1L : record.offset(),
                serialisePayload(payload),
                error == null ? null : error.getClass().getName(),
                error == null ? null : truncate(error.getMessage(), 2048),
                Timestamp.from(Instant.now()));
    }

    private String serialisePayload(Object value) {
        if (value == null) return null;
        if (value instanceof byte[] bytes) {
            return "{\"valueClass\":\"[B\",\"length\":" + bytes.length + "}";
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        safe.put("valueClass", value.getClass().getName());
        safe.put("redacted", true);
        try {
            return json.writeValueAsString(safe);
        } catch (JsonProcessingException e) {
            return "{\"redacted\":true}";
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}