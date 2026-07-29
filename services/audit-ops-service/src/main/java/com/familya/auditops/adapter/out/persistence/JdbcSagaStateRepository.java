package com.familya.auditops.adapter.out.persistence;

import com.familya.auditops.application.port.out.SagaStateRepository;
import com.familya.auditops.domain.model.SagaState;
import com.familya.auditops.domain.model.SagaStep;
import com.familya.auditops.domain.model.StepStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class JdbcSagaStateRepository implements SagaStateRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcSagaStateRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SagaState> findState(UUID operationId) {
        var rows = jdbc.queryForList(
                "SELECT operation_id, saga_type, current_step, compensating, step_count, last_transition_at, payload_json, version "
                        + "FROM saga_state WHERE operation_id = :id",
                new MapSqlParameterSource("id", operationId.toString()));
        if (rows.isEmpty()) return Optional.empty();
        var r = rows.get(0);
        return Optional.of(new SagaState(
                UUID.fromString((String) r.get("operation_id")),
                (String) r.get("saga_type"),
                (String) r.get("current_step"),
                Boolean.TRUE.equals(r.get("compensating")),
                ((Number) r.get("step_count")).intValue(),
                ((Timestamp) r.get("last_transition_at")).toInstant(),
                parseJson((String) r.get("payload_json")),
                ((Number) r.get("version")).longValue()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public SagaState saveState(SagaState s) {
        int updated = jdbc.update(
                "UPDATE saga_state SET current_step = :cs, compensating = :comp, step_count = :sc, "
                        + "last_transition_at = :lt, payload_json = :pl, version = :v WHERE operation_id = :id AND version = :ev",
                new MapSqlParameterSource()
                        .addValue("cs", s.currentStep())
                        .addValue("comp", s.compensating())
                        .addValue("sc", s.stepCount())
                        .addValue("lt", Timestamp.from(s.lastTransitionAt()))
                        .addValue("pl", json(s.payload()))
                        .addValue("v", s.version())
                        .addValue("id", s.operationId().toString())
                        .addValue("ev", s.version() - 1));
        if (updated == 0) {
            int inserted = jdbc.update(
                    "INSERT INTO saga_state (operation_id, saga_type, current_step, compensating, step_count, "
                            + "last_transition_at, payload_json, version) VALUES (:id, :st, :cs, :comp, :sc, :lt, :pl, :v)",
                    new MapSqlParameterSource()
                            .addValue("id", s.operationId().toString())
                            .addValue("st", s.sagaType())
                            .addValue("cs", s.currentStep())
                            .addValue("comp", s.compensating())
                            .addValue("sc", s.stepCount())
                            .addValue("lt", Timestamp.from(s.lastTransitionAt()))
                            .addValue("pl", json(s.payload()))
                            .addValue("v", s.version()));
            if (inserted == 0) {
                throw new com.familya.platform.error.OptimisticConcurrencyException(
                        "Saga state " + s.operationId() + " was modified concurrently.");
            }
        }
        return s;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SagaStep> listSteps(UUID operationId) {
        var rows = jdbc.queryForList(
                "SELECT operation_id, participant_service, step_name, sequence_no, status, target_revision, target_epoch, "
                        + "expected_version, acked_at, attempt_count, last_error_code, last_error_message, last_attempted_at, "
                        + "detail_json FROM saga_step WHERE operation_id = :id ORDER BY sequence_no",
                new MapSqlParameterSource("id", operationId.toString()));
        return rows.stream().map(this::stepFromRow).toList();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public SagaStep saveStep(SagaStep step) {
        int updated = jdbc.update(
                "UPDATE saga_step SET status = :status, target_revision = :tr, target_epoch = :te, "
                        + "expected_version = :ev, acked_at = :acked, attempt_count = :ac, "
                        + "last_error_code = :lec, last_error_message = :lem, last_attempted_at = :lat "
                        + "WHERE operation_id = :op AND participant_service = :ps AND step_name = :sn",
                stepParams(step, true));
        if (updated == 0) {
            jdbc.update(
                    "INSERT INTO saga_step (operation_id, participant_service, step_name, sequence_no, status, "
                            + "target_revision, target_epoch, expected_version, acked_at, attempt_count, "
                            + "last_error_code, last_error_message, last_attempted_at) "
                            + "VALUES (:op, :ps, :sn, :sq, :status, :tr, :te, :ev, :acked, :ac, :lec, :lem, :lat)",
                    stepParams(step, false));
        }
        return step;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public SagaStep transitionStep(UUID operationId,
                                   String participantService,
                                   String stepName,
                                   StepStatus next,
                                   String errorCode,
                                   String errorMessage,
                                   Instant when) {
        Optional<SagaStep> current = listSteps(operationId).stream()
                .filter(s -> s.participantService().equals(participantService) && s.stepName().equals(stepName))
                .findFirst();
        if (current.isEmpty()) {
            throw new com.familya.platform.error.NotFoundException(
                    "Saga step " + participantService + "/" + stepName + " not found");
        }
        SagaStep step = current.get();
        switch (next) {
            case DISPATCHED -> step.markDispatched(when);
            case ACKED -> step.markAcked(when);
            case FAILED -> step.markFailed(errorCode, errorMessage, when);
            case COMPENSATED -> step.markCompensated(when);
            case DEAD_LETTERED -> step.markDeadLettered(errorCode, errorMessage, when);
            default -> { /* PENDING: nothing to do */ }
        }
        return saveStep(step);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void deadLetterStep(UUID operationId,
                               String participantService,
                               String stepName,
                               String errorCode,
                               String errorMessage,
                               Map<String, Object> payload,
                               Instant when) {
        jdbc.update(
                "INSERT INTO saga_dead_letter (operation_id, participant_service, step_name, attempt_count, "
                        + "last_error_code, last_error_message, payload_json, quarantined_at) "
                        + "VALUES (:op, :ps, :sn, :ac, :ec, :em, :pl, :qa) "
                        + "ON DUPLICATE KEY UPDATE attempt_count = VALUES(attempt_count), "
                        + "last_error_code = VALUES(last_error_code), last_error_message = VALUES(last_error_message), "
                        + "payload_json = VALUES(payload_json), quarantined_at = VALUES(quarantined_at)",
                new MapSqlParameterSource()
                        .addValue("op", operationId.toString())
                        .addValue("ps", participantService)
                        .addValue("sn", stepName)
                        .addValue("ac", 0)
                        .addValue("ec", errorCode)
                        .addValue("em", errorMessage)
                        .addValue("pl", json(payload))
                        .addValue("qa", Timestamp.from(when)));
    }

    @Override
    @Transactional(readOnly = true)
    public long countByOperationAndStatus(UUID operationId, StepStatus status) {
        var rows = jdbc.queryForList(
                "SELECT COUNT(1) AS c FROM saga_step WHERE operation_id = :id AND status = :status",
                new MapSqlParameterSource()
                        .addValue("id", operationId.toString())
                        .addValue("status", status.name()));
        return rows.isEmpty() ? 0L : ((Number) rows.get(0).get("c")).longValue();
    }

    private MapSqlParameterSource stepParams(SagaStep s, boolean isUpdate) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("op", s.operationId().toString())
                .addValue("ps", s.participantService())
                .addValue("sn", s.stepName())
                .addValue("sq", s.sequenceNo())
                .addValue("status", s.status().name())
                .addValue("tr", s.targetRevision())
                .addValue("te", s.targetEpoch())
                .addValue("ev", s.expectedVersion())
                .addValue("acked", s.ackedAt() == null ? null : Timestamp.from(s.ackedAt()))
                .addValue("ac", s.attemptCount())
                .addValue("lec", s.lastErrorCode())
                .addValue("lem", s.lastErrorMessage())
                .addValue("lat", s.lastAttemptedAt() == null ? null : Timestamp.from(s.lastAttemptedAt()));
        return p;
    }

    private SagaStep stepFromRow(Map<String, Object> r) {
        return new SagaStep(
                UUID.fromString((String) r.get("operation_id")),
                (String) r.get("participant_service"),
                (String) r.get("step_name"),
                ((Number) r.get("sequence_no")).intValue(),
                StepStatus.valueOf((String) r.get("status")),
                r.get("target_revision") == null ? null : ((Number) r.get("target_revision")).longValue(),
                r.get("target_epoch") == null ? null : ((Number) r.get("target_epoch")).longValue(),
                r.get("expected_version") == null ? null : ((Number) r.get("expected_version")).longValue(),
                r.get("acked_at") == null ? null : ((Timestamp) r.get("acked_at")).toInstant(),
                ((Number) r.get("attempt_count")).intValue(),
                (String) r.get("last_error_code"),
                (String) r.get("last_error_message"),
                r.get("last_attempted_at") == null ? null : ((Timestamp) r.get("last_attempted_at")).toInstant(),
                parseJson((String) r.get("detail_json")));
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