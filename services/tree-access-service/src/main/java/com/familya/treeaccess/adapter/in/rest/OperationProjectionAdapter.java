package com.familya.treeaccess.adapter.in.rest;

import com.familya.platform.api.AsyncOperation;
import com.familya.platform.api.OperationQuery;
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
public class OperationProjectionAdapter implements OperationQuery {

    /** Template JDBC để truy vấn bảng {@code operation_audit}. */
    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Khởi tạo adapter với template JDBC dùng chung.
     *
     * @param jdbc template JDBC đã được Spring cấu hình
     */
    public OperationProjectionAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Tìm một {@link AsyncOperation} theo mã thao tác. Trả về {@link Optional#empty()}
     * khi bảng {@code operation_audit} không có bản ghi; trường hợp trạng thái
     * không hợp lệ thì mặc định là {@link AsyncOperation.Status#PENDING} để
     * tránh làm vỡ API.
     *
     * @param operationId mã thao tác cần truy vấn
     * @return {@link Optional} chứa {@link AsyncOperation} nếu tìm thấy
     */
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