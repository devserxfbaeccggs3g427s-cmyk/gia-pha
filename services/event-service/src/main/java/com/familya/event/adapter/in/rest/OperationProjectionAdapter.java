package com.familya.event.adapter.in.rest;

import com.familya.platform.api.AsyncOperation;
import com.familya.platform.api.OperationQuery;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class OperationProjectionAdapter implements OperationQuery {

    private final NamedParameterJdbcTemplate jdbc;

    public OperationProjectionAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<AsyncOperation> findById(UUID operationId) {
        var rows = jdbc.queryForList(
                "SELECT id, status FROM operation_audit WHERE id = :id",
                new MapSqlParameterSource("id", operationId.toString()));
        if (rows.isEmpty()) return Optional.empty();
        String status = (String) rows.get(0).get("status");
        AsyncOperation.Status s;
        try { s = AsyncOperation.Status.valueOf(status); }
        catch (IllegalArgumentException e) { s = AsyncOperation.Status.PENDING; }
        return Optional.of(new AsyncOperation(operationId, s,
                "/api/v2/operations/" + operationId, null, null, null, java.time.Instant.now()));
    }
}