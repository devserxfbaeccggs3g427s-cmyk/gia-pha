package com.familya.platform.idempotency;

import com.familya.platform.api.AsyncOperation;
import com.familya.platform.error.IdempotencyConflictException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Default MySQL-backed idempotency store. The table is created by the
 * service's Flyway migration; the schema is fixed:
 *
 * <pre>
 * CREATE TABLE idempotency_record (
 *   idempotency_key  VARCHAR(128) NOT NULL,
 *   service_name     VARCHAR(64)  NOT NULL,
 *   payload_hash     CHAR(64)     NOT NULL,
 *   operation_id     CHAR(36)     NOT NULL,
 *   response_body    JSON         NOT NULL,
 *   recorded_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 *   PRIMARY KEY (idempotency_key, service_name)
 * );
 * </pre>
 */
@Component
public class JdbcIdempotencyStore implements IdempotencyStore {

    private final NamedParameterJdbcTemplate jdbc;
    private final String serviceName;

    @Autowired
    public JdbcIdempotencyStore(NamedParameterJdbcTemplate jdbc,
                                org.springframework.core.env.Environment env) {
        this.jdbc = jdbc;
        this.serviceName = env.getProperty("spring.application.name", "unknown");
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AsyncOperation> find(String key) {
        var rows = jdbc.queryForList(
                "SELECT operation_id, response_body FROM idempotency_record WHERE idempotency_key = :k AND service_name = :s",
                new MapSqlParameterSource().addValue("k", key).addValue("s", serviceName));
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        // The response_body is a JSON envelope; the consumer (e.g. the
        // gateway) deserialises the original `AsyncOperation` here.
        UUID opId = UUID.fromString((String) rows.get(0).get("operation_id"));
        return Optional.of(AsyncOperation.accepted(opId, "/api/v2/operations/" + opId));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(String key, String payloadHash, AsyncOperation operation) {
        var existing = jdbc.queryForList(
                "SELECT payload_hash FROM idempotency_record WHERE idempotency_key = :k AND service_name = :s FOR UPDATE",
                new MapSqlParameterSource().addValue("k", key).addValue("s", serviceName));
        if (!existing.isEmpty()) {
            String stored = (String) existing.get(0).get("payload_hash");
            if (!stored.equals(payloadHash)) {
                throw new IdempotencyConflictException(
                        "Idempotency key reused with a different payload hash.");
            }
            return;
        }
        jdbc.update(
                "INSERT INTO idempotency_record (idempotency_key, service_name, payload_hash, operation_id, response_body) "
                        + "VALUES (:k, :s, :h, :o, :b)",
                new MapSqlParameterSource()
                        .addValue("k", key)
                        .addValue("s", serviceName)
                        .addValue("h", payloadHash)
                        .addValue("o", operation.operationId().toString())
                        .addValue("b", "{\"status\":\"" + operation.status() + "\"}"));
    }
}
