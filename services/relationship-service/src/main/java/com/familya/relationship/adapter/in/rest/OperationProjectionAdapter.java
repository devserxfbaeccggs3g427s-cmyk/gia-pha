package com.familya.relationship.adapter.in.rest;

import com.familya.platform.api.AsyncOperation;
import com.familya.platform.api.OperationQuery;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Adapter cho phép các service khác truy vấn trạng thái của một thao tác bất
 * đồng bộ (async operation) thông qua giao diện {@link OperationQuery} của
 * platform.
 * <p>
 * Thông tin được lấy từ bảng {@code operation_audit} - bảng này được populate
 * bởi các use case trong hệ thống. Adapter ánh xạ trạng thái {@code status}
 * sang enum {@link AsyncOperation.Status}; nếu không nhận dạng được thì trả về
 * {@link AsyncOperation.Status#PENDING} như một giá trị an toàn mặc định.
 * </p>
 */
@Component
public class OperationProjectionAdapter implements OperationQuery {

    /** Template JDBC dùng để truy vấn bảng operation_audit. */
    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Khởi tạo adapter.
     *
     * @param jdbc template JDBC
     */
    public OperationProjectionAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Quy trình:
     * </p>
     * <ol>
     *   <li>Truy vấn {@code operation_audit} theo {@code operationId}.</li>
     *   <li>Nếu không có dòng nào → {@link Optional#empty()}.</li>
     *   <li>Ánh xạ {@code status} sang enum; nếu parse lỗi thì dùng {@code PENDING}.</li>
     *   <li>Trả về {@link AsyncOperation} với URL polling.</li>
     * </ol>
     *
     * @param operationId định danh thao tác
     * @return {@code Optional} chứa {@link AsyncOperation} hoặc rỗng
     */
    @Override
    public Optional<AsyncOperation> findById(UUID operationId) {
        var rows = jdbc.queryForList(
                "SELECT id, status FROM operation_audit WHERE id = :id",
                new MapSqlParameterSource("id", operationId.toString()));
        if (rows.isEmpty()) return Optional.empty();
        String status = (String) rows.get(0).get("status");
        AsyncOperation.Status s;
        // Parse status sang enum; nếu không nhận dạng được thì mặc định PENDING
        // (an toàn để client tiếp tục polling).
        try { s = AsyncOperation.Status.valueOf(status); }
        catch (IllegalArgumentException e) { s = AsyncOperation.Status.PENDING; }
        return Optional.of(new AsyncOperation(operationId, s,
                "/api/v2/operations/" + operationId, null, null, null, java.time.Instant.now()));
    }
}