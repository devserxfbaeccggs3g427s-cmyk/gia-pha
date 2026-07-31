package com.familya.treeaccess.adapter.in.rest;

import com.familya.platform.api.AsyncOperation;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Read-side adapter for the operation envelope. The owning service
 * owns the operation lifecycle; this adapter answers the public
 * {@code GET /api/v2/operations/{operationId}} route from the local
 * {@code operation_audit} projection.
 */
@Component
public class OperationProjectionAdapter implements com.familya.platform.api.OperationQuery {

    private final NamedParameterJdbcTemplate jdbc;

    public OperationProjectionAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<AsyncOperation> findById(UUID operationId) {
        var rows = jdbc.queryForList(
                "SELECT id, status, updated_at FROM operation_audit WHERE id = :id",
                new MapSqlParameterSource("id", operationId.toString()));
        if (rows.isEmpty()) return Optional.empty();
        var r = rows.get(0);
        String status = (String) r.get("status");
        AsyncOperation.Status s = mapStatus(status);
        java.time.Instant updatedAt = r.get("updated_at") == null
                ? java.time.Instant.now()
                : ((java.sql.Timestamp) r.get("updated_at")).toInstant();
        return Optional.of(new AsyncOperation(operationId, s,
                "/api/v2/operations/" + operationId, null, null, null, updatedAt));
    }

    /**
     * Map Saga-internal {@code FREEZING}/{@code TOMBSTONING}/{@code PURGING}/{@code FINALIZING}
     * to public {@link AsyncOperation.Status} value {@code RUNNING}; unknown values
     * fall back to {@code PENDING}.
     */
    private static AsyncOperation.Status mapStatus(String raw) {
        if (raw == null) return AsyncOperation.Status.PENDING;
        try {
            return AsyncOperation.Status.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return switch (raw) {
                case "FREEZING", "TOMBSTONING", "PURGING", "FINALIZING", "DISPATCHED" ->
                        AsyncOperation.Status.RUNNING;
                default -> AsyncOperation.Status.PENDING;
            };
        }
    }
}
