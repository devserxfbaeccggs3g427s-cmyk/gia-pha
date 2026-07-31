package com.familya.member.adapter.in.rest;

import com.familya.platform.api.AsyncOperation;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Adapter cho phép các dịch vụ khác truy vấn trạng thái của một operation Saga đang chạy trong
 * Member Service. Triển khai {@link com.familya.platform.api.OperationQuery} bằng cách tra cứu
 * bảng {@code operation_audit}.
 *
 * <p>Bean {@code @Component} thuộc tầng adapter-in trong kiến trúc Hexagonal.</p>
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
                "SELECT id, status, started_at, updated_at, finished_at FROM operation_audit WHERE id = :id",
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
     * Map Saga-internal status ({@code DISPATCHED}) sang public
     * {@link AsyncOperation.Status} value {@code RUNNING} theo
     * ADR-007 / Task 13.1. Unknown values fall back to {@code PENDING}.
     */
    private static AsyncOperation.Status mapStatus(String raw) {
        if (raw == null) return AsyncOperation.Status.PENDING;
        try {
            return AsyncOperation.Status.valueOf(raw);
        } catch (IllegalArgumentException e) {
            if ("DISPATCHED".equals(raw)) return AsyncOperation.Status.RUNNING;
            return AsyncOperation.Status.PENDING;
        }
    }
}
