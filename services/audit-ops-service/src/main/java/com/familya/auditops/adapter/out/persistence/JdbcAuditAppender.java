package com.familya.auditops.adapter.out.persistence;

import com.familya.auditops.application.port.out.AuditAppender;
import com.familya.auditops.domain.model.AuditEvent;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class JdbcAuditAppender implements AuditAppender {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcAuditAppender(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEvent append(AuditEvent e) {
        jdbc.update(
                "INSERT INTO audit_event (audit_id, operation_id, correlation_id, actor_user_id, actor_kind, "
                        + "action, target_type, target_id, detail_json, occurred_at, trace_id) "
                        + "VALUES (:id, :op, :co, :au, :ak, :action, :tt, :ti, :detail, :oa, :tr)",
                new MapSqlParameterSource()
                        .addValue("id", e.auditId().toString())
                        .addValue("op", e.operationId() == null ? null : e.operationId().toString())
                        .addValue("co", e.correlationId() == null ? null : e.correlationId().toString())
                        .addValue("au", e.actorUserId() == null ? null : e.actorUserId().toString())
                        .addValue("ak", e.actorKind().name())
                        .addValue("action", e.action())
                        .addValue("tt", e.targetType())
                        .addValue("ti", e.targetId())
                        .addValue("detail", json(e.detail()))
                        .addValue("oa", Timestamp.from(e.occurredAt()))
                        .addValue("tr", e.traceId()));
        return e;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditEvent> findByOperation(UUID operationId, int limit) {
        var rows = jdbc.queryForList(
                "SELECT audit_id, operation_id, correlation_id, actor_user_id, actor_kind, action, target_type, "
                        + "target_id, detail_json, occurred_at, trace_id FROM audit_event "
                        + "WHERE operation_id = :op ORDER BY occurred_at DESC LIMIT :l",
                new MapSqlParameterSource()
                        .addValue("op", operationId.toString())
                        .addValue("l", limit));
        return rows.stream().map(this::fromRow).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AuditEvent> findById(UUID auditId) {
        var rows = jdbc.queryForList(
                "SELECT audit_id, operation_id, correlation_id, actor_user_id, actor_kind, action, target_type, "
                        + "target_id, detail_json, occurred_at, trace_id FROM audit_event WHERE audit_id = :id",
                new MapSqlParameterSource("id", auditId.toString()));
        if (rows.isEmpty()) return Optional.empty();
        return Optional.of(fromRow(rows.get(0)));
    }

    private AuditEvent fromRow(Map<String, Object> r) {
        return new AuditEvent(
                UUID.fromString((String) r.get("audit_id")),
                r.get("operation_id") == null ? null : UUID.fromString((String) r.get("operation_id")),
                r.get("correlation_id") == null ? null : UUID.fromString((String) r.get("correlation_id")),
                r.get("actor_user_id") == null ? null : UUID.fromString((String) r.get("actor_user_id")),
                AuditEvent.ActorKind.valueOf((String) r.get("actor_kind")),
                (String) r.get("action"),
                (String) r.get("target_type"),
                (String) r.get("target_id"),
                parseJson((String) r.get("detail_json")),
                ((Timestamp) r.get("occurred_at")).toInstant(),
                (String) r.get("trace_id"));
    }

    private static String json(Map<String, Object> m) {
        if (m == null || m.isEmpty()) return null;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(m);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot serialise JSON", e);
        }
    }

    private static Map<String, Object> parseJson(String s) {
        if (s == null || s.isBlank()) return Map.of();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(s, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            return Map.of();
        }
    }
}