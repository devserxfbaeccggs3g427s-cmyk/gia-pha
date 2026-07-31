package com.familya.sharing.adapter.in.rest;

import com.familya.platform.api.AsyncOperation;
import com.familya.platform.api.OperationQuery;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Adapter triển khai {@link OperationQuery} để phục vụ truy vấn trạng thái
 * thao tác bất đồng bộ cho sharing-service.
 * <p>
 * Truy vấn này được sử dụng bởi các dịch vụ khác (hoặc API gateway) để lấy
 * trạng thái hiện tại của một thao tác dựa trên {@code operationId}, đọc từ
 * bảng {@code operation_audit} (bảng dùng chung ở tầng platform).
 */
@Component
public class OperationProjectionAdapter implements OperationQuery {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Khởi tạo adapter.
     *
     * @param jdbc template JDBC chia sẻ.
     */
    public OperationProjectionAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Tìm một {@link AsyncOperation} theo {@code operationId}.
     *
     * @param operationId định danh thao tác.
     * @return {@link Optional} chứa {@link AsyncOperation} nếu tồn tại &mdash;
     *         ngược lại là {@link Optional#empty()}.
     */
    @Override
    public Optional<AsyncOperation> findById(UUID operationId) {
        // Bước 1: Truy vấn đơn giản lấy id & status từ bảng operation_audit.
        var rows = jdbc.queryForList(
                "SELECT id, status FROM operation_audit WHERE id = :id",
                new MapSqlParameterSource("id", operationId.toString()));

        // Bước 2: Trả về Optional.empty() nếu không tìm thấy.
        if (rows.isEmpty()) return Optional.empty();

        // Bước 3: Chuyển đổi status từ String sang enum &mdash; fallback về PENDING nếu không hợp lệ.
        String status = (String) rows.get(0).get("status");
        AsyncOperation.Status s;
        try { s = AsyncOperation.Status.valueOf(status); }
        catch (IllegalArgumentException e) { s = AsyncOperation.Status.PENDING; }

        // Bước 4: Đóng gói kết quả với URL tra cứu theo chuẩn API v2.
        return Optional.of(new AsyncOperation(operationId, s,
                "/api/v2/operations/" + operationId, null, null, null, java.time.Instant.now()));
    }
}