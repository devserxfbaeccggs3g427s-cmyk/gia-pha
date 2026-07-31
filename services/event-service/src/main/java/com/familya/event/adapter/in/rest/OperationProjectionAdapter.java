package com.familya.event.adapter.in.rest;

import com.familya.platform.api.AsyncOperation;
import com.familya.platform.api.OperationQuery;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter triển khai {@link OperationQuery} cho event-service bằng cách
 * truy vấn bảng {@code operation_audit} (cập nhật bởi outbox / sagas).
 *
 * <p>Endpoint {@code /api/v2/operations/{operationId}} chung của platform
 * sẽ dùng bean này để trả về {@link AsyncOperation} cho client.
 *
 * @author gia-pha platform
 */
@Component
public class OperationProjectionAdapter implements OperationQuery {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Khởi tạo adapter.
     *
     * @param jdbc JDBC template dùng chung.
     */
    public OperationProjectionAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Tra cứu {@link AsyncOperation} theo {@code operationId}.
     *
     * <p>Quy trình:
     * <ol>
     *   <li>Truy vấn {@code SELECT id, status FROM operation_audit WHERE id = :id}.</li>
     *   <li>Nếu không có bản ghi → trả {@link Optional#empty()}.</li>
     *   <li>Nếu có, ánh xạ {@code status} sang {@link AsyncOperation.Status}
     *       (mặc định {@code PENDING} nếu không nhận diện được).</li>
     *   <li>Trả {@link AsyncOperation} với {@code occurredAt = Instant.now()}
     *       — không có timestamp chính xác trong bảng này.</li>
     * </ol>
     *
     * @param operationId định danh operation cần truy vấn.
     * @return {@link Optional} chứa {@link AsyncOperation} hoặc rỗng.
     */
    @Override
    public Optional<AsyncOperation> findById(UUID operationId) {
        // Bước 1: truy vấn bảng operation_audit.
        var rows = jdbc.queryForList(
                "SELECT id, status FROM operation_audit WHERE id = :id",
                new MapSqlParameterSource("id", operationId.toString()));
        if (rows.isEmpty()) return Optional.empty();

        // Bước 2: đọc status và ánh xạ an toàn.
        String status = (String) rows.get(0).get("status");
        AsyncOperation.Status s;
        try { s = AsyncOperation.Status.valueOf(status); }
        catch (IllegalArgumentException e) { s = AsyncOperation.Status.PENDING; }

        // Bước 3: trả về AsyncOperation với location trỏ về endpoint truy vấn.
        return Optional.of(new AsyncOperation(operationId, s,
                "/api/v2/operations/" + operationId, null, null, null, java.time.Instant.now()));
    }

    /**
     * Trả về {@link Instant} hiện tại — tiện ích nội bộ để tránh import lặp.
     *
     * @return {@link Instant#now()}.
     */
    @SuppressWarnings("unused")
    private Instant now() { return Instant.now(); }
}
