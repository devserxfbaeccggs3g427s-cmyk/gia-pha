package com.familya.search.adapter.in.rest;

import com.familya.platform.api.AsyncOperation;
import com.familya.platform.api.OperationQuery;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Adapter cho phép nền tảng chung truy vấn trạng thái thao tác (operation)
 * bằng cách đọc từ bảng {@code operation_audit} của search service.
 *
 * <p>Đây là triển khai {@link OperationQuery} rất tối giản: chỉ cung cấp
 * {@code id} và {@code status}. Nếu trạng thái trong DB không phải giá trị
 * enum hợp lệ thì fallback về {@link AsyncOperation.Status#PENDING}.</p>
 */
@Component
public class OperationProjectionAdapter implements OperationQuery {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Khởi tạo adapter với JDBC template dùng chung.
     *
     * @param jdbc JDBC template dùng để truy vấn {@code operation_audit}.
     */
    public OperationProjectionAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Tìm trạng thái thao tác theo {@code operationId}.
     *
     * @param operationId định danh thao tác.
     * @return {@code Optional} chứa {@link AsyncOperation} nếu tồn tại,
     *         ngược lại rỗng.
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
        // Status lạ trong DB (do phiên bản cũ/seed) - fallback về PENDING
        // để client nhận trạng thái an toàn.
        catch (IllegalArgumentException e) { s = AsyncOperation.Status.PENDING; }
        return Optional.of(new AsyncOperation(operationId, s,
                "/api/v2/operations/" + operationId, null, null, null, java.time.Instant.now()));
    }
}
