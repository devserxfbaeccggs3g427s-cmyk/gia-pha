package com.familya.platform.idempotency;

import com.familya.platform.api.AsyncOperation;
import com.familya.platform.error.IdempotencyConflictException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Triển khai mặc định của {@link IdempotencyStore} sử dụng MySQL làm kho lưu trữ.
 *
 * <p>Bảng {@code idempotency_record} được tạo bởi migration Flyway của dịch vụ;
 * schema cố định như sau:</p>
 *
 * <pre>
 * CREATE TABLE idempotency_record (
 *   idempotency_key  VARCHAR(128) NOT NULL,
 *   service_name     VARCHAR(64)  NOT NULL,
 *   payload_hash     CHAR(64)     NOT NULL,
 *   operation_id     CHAR(36)     NOT NULL,
 *   response_body    JSON         NOT NULL,
 *   recorded_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 *   PRIMARY KEY (idempotency_key, service_name)
 * );
 * </pre>
 *
 * <p><b>Khóa chính tổng hợp</b> {@code (idempotency_key, service_name)} đảm bảo
 * các dịch vụ khác nhau có thể dùng cùng key mà không xung đột. Mỗi dịch vụ
 * tự inject {@link JdbcIdempotencyStore} và sử dụng {@code spring.application.name}
 * làm {@code service_name}.</p>
 *
 * @author Family Tree Platform Team
 */
@Component
public class JdbcIdempotencyStore implements IdempotencyStore {

    private final NamedParameterJdbcTemplate jdbc;

    /** Tên dịch vụ hiện tại, được lấy từ {@code spring.application.name}. */
    private final String serviceName;

    /**
     * Khởi tạo store với {@link NamedParameterJdbcTemplate} và môi trường Spring.
     *
     * @param jdbc template JDBC dùng để thực thi truy vấn
     * @param env  môi trường Spring để đọc {@code spring.application.name}
     */
    @Autowired
    public JdbcIdempotencyStore(NamedParameterJdbcTemplate jdbc,
                                org.springframework.core.env.Environment env) {
        this.jdbc = jdbc;
        // Mặc định serviceName = "unknown" nếu không cấu hình, giúp tránh null.
        this.serviceName = env.getProperty("spring.application.name", "unknown");
    }

    /**
     * Tìm kiếm thao tác theo key.
     *
     * <p>Trả về envelope {@link AsyncOperation} ở trạng thái {@code PENDING}
     * cùng URL trạng thái tương ứng. Trong triển khai thực tế, {@code response_body}
     * (JSON) có thể được deserialize đầy đủ để tái tạo envelope nguyên bản.</p>
     *
     * @param key idempotency key
     * @return {@link Optional} chứa envelope nếu tìm thấy
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<AsyncOperation> find(String key) {
        // Bước 1: Truy vấn bảng idempotency_record theo cặp (key, service_name).
        var rows = jdbc.queryForList(
                "SELECT operation_id, response_body FROM idempotency_record WHERE idempotency_key = :k AND service_name = :s",
                new MapSqlParameterSource().addValue("k", key).addValue("s", serviceName));

        // Bước 2: Nếu không tìm thấy, trả về Optional.empty() để caller biết
        // đây là lần đầu ghi nhận key này.
        if (rows.isEmpty()) {
            return Optional.empty();
        }

        // Bước 3: response_body là JSON envelope; consumer (ví dụ gateway)
        // sẽ deserialize lại AsyncOperation tại đây nếu cần toàn bộ trạng thái.
        UUID opId = UUID.fromString((String) rows.get(0).get("operation_id"));

        // Bước 4: Trả về envelope tối thiểu (PENDING + URL trạng thái) đủ để
        // client có thể polling hoặc tái sử dụng.
        return Optional.of(AsyncOperation.accepted(opId, "/api/v2/operations/" + opId));
    }

    /**
     * Ghi nhận thao tác với idempotency key. Sử dụng {@code SELECT ... FOR UPDATE}
     * để khoá hàng đang tồn tại (nếu có) trong transaction, đảm bảo tính nhất
     * quán giữa các lần ghi đồng thời.
     *
     * @param key         idempotency key
     * @param payloadHash hash payload
     * @param operation   envelope thao tác cần ghi nhận
     * @throws IdempotencyConflictException nếu key đã tồn tại với payload hash khác
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(String key, String payloadHash, AsyncOperation operation) {
        // Bước 1: Khoá và đọc bản ghi hiện tại (nếu có) bằng SELECT ... FOR UPDATE.
        // Khoá này đảm bảo chỉ một transaction có thể kiểm tra/ghi tại một thời điểm.
        var existing = jdbc.queryForList(
                "SELECT payload_hash FROM idempotency_record WHERE idempotency_key = :k AND service_name = :s FOR UPDATE",
                new MapSqlParameterSource().addValue("k", key).addValue("s", serviceName));

        // Bước 2: Nếu đã có bản ghi, so sánh payload hash:
        // - Khớp: idempotent, không cần làm gì thêm.
        // - Khác: ném IdempotencyConflictException để báo lỗi cho client.
        if (!existing.isEmpty()) {
            String stored = (String) existing.get(0).get("payload_hash");
            if (!stored.equals(payloadHash)) {
                throw new IdempotencyConflictException(
                        "Idempotency key reused with a different payload hash.");
            }
            return;
        }

        // Bước 3: Nếu chưa có bản ghi, thực hiện INSERT với các tham số đã chuẩn bị.
        // response_body ở đây được lưu tối thiểu (chỉ status) — triển khai thực
        // tế nên serialize toàn bộ envelope để tái sử dụng đầy đủ khi replay.
        jdbc.update(
                "INSERT INTO idempotency_record (idempotency_key, service_name, payload_hash, operation_id, response_body) "
                        + "VALUES (:k, :s, :h, :o, :b)",
                new MapSqlParameterSource()
                        .addValue("k", key)
                        .addValue("s", serviceName)
                        .addValue("h", payloadHash)
                        .addValue("o", operation.operationId().toString())
                        .addValue("b", "{\"status\":\"" + operation.status() + "\"}"));
    }
}
