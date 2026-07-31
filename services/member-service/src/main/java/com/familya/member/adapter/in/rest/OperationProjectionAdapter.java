package com.familya.member.adapter.in.rest;

import com.familya.platform.api.AsyncOperation;
import com.familya.platform.api.OperationQuery;
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
 * <p>Bean {@code @Component} thuộc tầng adapter-in trong kiến trúc Hexagonal.
 */
@Component
public class OperationProjectionAdapter implements OperationQuery {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Khởi tạo adapter với JDBC template.
     *
     * @param jdbc template JDBC dùng để truy vấn bảng operation_audit
     */
    public OperationProjectionAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Tra cứu trạng thái operation theo mã operationId.
     *
     * @param operationId mã operation cần tra cứu
     * @return {@link AsyncOperation} nếu tìm thấy, {@link Optional#empty()} nếu không
     */
    @Override
    public Optional<AsyncOperation> findById(UUID operationId) {
        // Truy vấn nhanh chỉ lấy id và status để giảm chi phí I/O
        var rows = jdbc.queryForList(
                "SELECT id, status FROM operation_audit WHERE id = :id",
                new MapSqlParameterSource("id", operationId.toString()));
        if (rows.isEmpty()) return Optional.empty();
        String status = (String) rows.get(0).get("status");
        AsyncOperation.Status s;
        // Ánh xạ status từ CSDL sang enum; nếu không hợp lệ thì mặc định là PENDING
        try { s = AsyncOperation.Status.valueOf(status); }
        catch (IllegalArgumentException e) { s = AsyncOperation.Status.PENDING; }
        return Optional.of(new AsyncOperation(operationId, s,
                "/api/v2/operations/" + operationId, null, null, null, java.time.Instant.now()));
    }
}