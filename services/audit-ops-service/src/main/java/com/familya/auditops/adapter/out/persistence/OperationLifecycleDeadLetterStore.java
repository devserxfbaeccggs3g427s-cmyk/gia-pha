package com.familya.auditops.adapter.out.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Component
public class OperationLifecycleDeadLetterStore {
    static final String CONSUMER = "audit-ops-service.lifecycle";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public OperationLifecycleDeadLetterStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void save(ConsumerRecord<String, Object> record, Throwable error) {
        jdbc.update("""
                INSERT INTO saga_dead_letter
                    (operation_id, participant_service, step_name, attempt_count,
                     last_error_code, last_error_message, payload_json, quarantined_at)
                VALUES (?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE
                    attempt_count = VALUES(attempt_count),
                    last_error_code = VALUES(last_error_code),
                    last_error_message = VALUES(last_error_message),
                    payload_json = VALUES(payload_json),
                    quarantined_at = VALUES(quarantined_at)
                """,
                UUID.nameUUIDFromBytes((CONSUMER + "|" + record.topic() + "|"
                        + record.partition() + "|" + record.offset()).getBytes()).toString(),
                CONSUMER, record.topic(), (long) record.offset(),
                error == null ? null : error.getClass().getName(),
                error == null ? null : truncate(error.getMessage(), 2048),
                serialisePayload(record.value()),
                Timestamp.from(Instant.now()));
    }

    private String serialisePayload(Object value) {
        if (value == null) return null;
        try {
            return json.writeValueAsString(value instanceof String ? value : String.valueOf(value));
        } catch (Exception e) {
            return "null";
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}