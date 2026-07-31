package com.familya.media.adapter.in.rest;

import com.familya.platform.api.AsyncOperation;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Adapter đầu vào (inbound) — cầu nối giữa bảng {@code operation_audit} của
 * media-service và API {@code GET /api/v2/operations/{id}} của platform.
 * <p>
 * Triển khai {@link com.familya.platform.api.OperationQuery} bằng cách đọc
 * thẳng từ bảng {@code operation_audit} nội bộ thay vì gọi sang audit-ops, nhờ
 * đó phản hồi đồng bộ mà không cần tra cứu chéo service.
 */
@Component
public class OperationProjectionAdapter implements com.familya.platform.api.OperationQuery {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Khởi tạo adapter.
     *
     * @param jdbc JDBC template để truy vấn {@code operation_audit}.
     */
    public OperationProjectionAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Tra cứu trạng thái của một operation theo UUID.
     * <p>
     * Nếu không tìm thấy row hoặc status không phải enum hợp lệ, trả về
     * {@code PENDING} để tránh rò rỉ trạng thái "FAILED" cho caller.
     *
     * @param operationId UUID operation cần tra.
     * @return Optional chứa {@link AsyncOperation} nếu tồn tại; empty nếu không.
     */
    @Override
    public Optional<AsyncOperation> findById(UUID operationId) {
        var rows = jdbc.queryForList(
                "SELECT id, status FROM operation_audit WHERE id = :id",
                new MapSqlParameterSource("id", operationId.toString()));
        if (rows.isEmpty()) return Optional.empty();
        String status = (String) rows.get(0).get("status");
        AsyncOperation.Status s;
        // Fail-safe: status lạ (do version cũ hoặc dữ liệu bẩn) coi như PENDING.
        try { s = AsyncOperation.Status.valueOf(status); }
        catch (IllegalArgumentException e) { s = AsyncOperation.Status.PENDING; }
        return Optional.of(new AsyncOperation(operationId, s,
                "/api/v2/operations/" + operationId, null, null, null, java.time.Instant.now()));
    }
}
