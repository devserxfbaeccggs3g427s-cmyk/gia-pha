/**
 * Append-only store for operator intent and audit timeline entries.
 *
 * <p>Replaces the legacy {@code OperationService}/{@code SagaOrchestrator}
 * write paths. The store only inserts rows; it never mutates authoritative
 * Saga state (which lives in the owning service). All payloads are
 * redacted through {@link com.familya.auditops.application.usecase.PayloadRedactor}
 * before insert so secrets, raw Blob URLs, signed capabilities, and
 * non-allowlisted PII never reach the table.</p>
 */
package com.familya.auditops.adapter.out.persistence;

import com.familya.auditops.application.usecase.PayloadRedactor;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class OperatorIntentStore {

    private final NamedParameterJdbcTemplate jdbc;
    private final PayloadRedactor redactor;

    public OperatorIntentStore(NamedParameterJdbcTemplate jdbc, PayloadRedactor redactor) {
        this.jdbc = jdbc;
        this.redactor = redactor;
    }

    public UUID recordIntent(String action, UUID operationId, UUID actorUserId,
                             String actorKind, Map<String, Object> payload, List<String> roles) {
        Map<String, Object> enriched = new LinkedHashMap<>(payload);
        if (roles != null && !roles.isEmpty()) {
            enriched.put("actorRoles", roles);
        }
        JsonNode redacted = redactor.redact(toJson(enriched)).orElse(null);
        UUID eventId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO audit_evidence
                    (event_id, operation_id, correlation_id, causation_id, source_service,
                     actor_user_id, actor_kind, outcome, reason_code, payload_json, occurred_at)
                VALUES (:eid,:op,:co,:ca,:svc,:actor,:kind,:outcome,:reason,:payload,:when)
                """,
                new MapSqlParameterSource()
                        .addValue("eid", eventId.toString())
                        .addValue("op", operationId == null ? null : operationId.toString())
                        .addValue("co", null)
                        .addValue("ca", null)
                        .addValue("svc", "audit-ops-service")
                        .addValue("actor", actorUserId == null ? null : actorUserId.toString())
                        .addValue("kind", actorKind)
                        .addValue("outcome", "INTENT_RECORDED")
                        .addValue("reason", action)
                        .addValue("payload", redacted == null ? null : redacted.toString())
                        .addValue("when", Timestamp.from(Instant.now())));
        return eventId;
    }

    public List<Map<String, Object>> findByOperation(UUID operationId, int limit) {
        var rows = jdbc.queryForList(
                "SELECT event_id, operation_id, actor_user_id, actor_kind, outcome, reason_code, payload_json, occurred_at "
                        + "FROM audit_evidence WHERE operation_id = :op ORDER BY occurred_at DESC LIMIT :lim",
                new MapSqlParameterSource().addValue("op", operationId.toString()).addValue("lim", limit));
        return rows.stream().map(this::rowToView).toList();
    }

    public List<Map<String, Object>> findOperatorActions(int limit) {
        var rows = jdbc.queryForList(
                "SELECT event_id, operation_id, actor_user_id, actor_kind, outcome, reason_code, payload_json, occurred_at "
                        + "FROM audit_evidence WHERE actor_kind = 'OPERATOR' ORDER BY occurred_at DESC LIMIT :lim",
                new MapSqlParameterSource("lim", limit));
        return rows.stream().map(this::rowToView).toList();
    }

    public List<Map<String, Object>> findLifecycleDeadLetter(int limit) {
        var rows = jdbc.queryForList(
                "SELECT event_id, topic, partition_no, offset_no, operation_id, error_class, error_message, redacted_payload_json, quarantined_at "
                        + "FROM lifecycle_dead_letter ORDER BY quarantined_at DESC LIMIT :lim",
                new MapSqlParameterSource("lim", limit));
        List<Map<String, Object>> out = new ArrayList<>();
        for (var r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("stream", "operations.events.v1");
            m.put("eventId", r.get("event_id"));
            m.put("topic", r.get("topic"));
            m.put("partition", r.get("partition_no"));
            m.put("offset", r.get("offset_no"));
            m.put("operationId", r.get("operation_id"));
            m.put("errorClass", r.get("error_class"));
            m.put("errorMessage", r.get("error_message"));
            m.put("redactedPayload", r.get("redacted_payload_json"));
            m.put("quarantinedAt", r.get("quarantined_at"));
            out.add(m);
        }
        return out;
    }

    public List<Map<String, Object>> findSagaReplyDeadLetter(int limit) {
        var rows = jdbc.queryForList(
                "SELECT event_id, topic, partition_no, offset_no, operation_id, participant_service, step_name, attempt_count, error_class, error_message, redacted_payload_json, quarantined_at "
                        + "FROM saga_reply_dead_letter ORDER BY quarantined_at DESC LIMIT :lim",
                new MapSqlParameterSource("lim", limit));
        List<Map<String, Object>> out = new ArrayList<>();
        for (var r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("stream", "saga.replies.v1");
            m.put("eventId", r.get("event_id"));
            m.put("topic", r.get("topic"));
            m.put("partition", r.get("partition_no"));
            m.put("offset", r.get("offset_no"));
            m.put("operationId", r.get("operation_id"));
            m.put("participantService", r.get("participant_service"));
            m.put("stepName", r.get("step_name"));
            m.put("attemptCount", r.get("attempt_count"));
            m.put("errorClass", r.get("error_class"));
            m.put("errorMessage", r.get("error_message"));
            m.put("redactedPayload", r.get("redacted_payload_json"));
            m.put("quarantinedAt", r.get("quarantined_at"));
            out.add(m);
        }
        return out;
    }

    private Map<String, Object> rowToView(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("eventId", r.get("event_id"));
        m.put("operationId", r.get("operation_id"));
        m.put("actorUserId", r.get("actor_user_id"));
        m.put("actorKind", r.get("actor_kind"));
        m.put("outcome", r.get("outcome"));
        m.put("reasonCode", r.get("reason_code"));
        m.put("payload", r.get("payload_json"));
        m.put("occurredAt", r.get("occurred_at"));
        return m;
    }

    private JsonNode toJson(Object value) {
        if (value == null) return null;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(value);
        } catch (Exception e) {
            return null;
        }
    }
}
