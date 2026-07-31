/**
 * Kho dead-letter cho các sự kiện vòng đời operation không xử lý được.
 *
 * <p>Lưu các message lỗi vào bảng {@code saga_dead_letter} với khoá
 * ổn định sinh từ {@code consumer|topic|partition|offset} để idempotent.</p>
 */
package com.familya.auditops.adapter.out.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Component lưu trữ các message Kafka lỗi để phục vụ debug và xử lý
 * thủ công sau này.
 *
 * <p>ID của row được sinh bằng {@code UUID.nameUUIDFromBytes} với input
 * {@code consumer|topic|partition|offset} - đảm bảo cùng một vị trí lỗi
 * luôn được ghi đè lên cùng một row, tránh phình DLQ.</p>
 */
@Component
public class OperationLifecycleDeadLetterStore {

    /** Tên consumer dùng để xác định nguồn gốc. */
    static final String CONSUMER = "audit-ops-service.lifecycle";

    /** JDBC template. */
    private final JdbcTemplate jdbc;
    /** Object mapper dùng để serialize payload. */
    private final ObjectMapper json;

    /**
     * Khởi tạo kho DLQ.
     *
     * @param jdbc JDBC template
     * @param json object mapper
     */
    public OperationLifecycleDeadLetterStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * Lưu một bản ghi Kafka lỗi vào bảng {@code saga_dead_letter}.
     *
     * <p>Các bước:</p>
     * <ol>
     *   <li>Sinh id ổn định từ consumer/topic/partition/offset.</li>
     *   <li>Lưu mã lỗi và thông điệp (cắt ngắn nếu quá dài).</li>
     *   <li>Serialize payload (hoặc {@code "null"} nếu lỗi).</li>
     *   <li>Ghi nhận thời điểm quarantine.</li>
     * </ol>
     *
     * @param record bản ghi Kafka lỗi
     * @param error  exception gây lỗi (có thể null)
     */
    public void save(ConsumerRecord<String, Object> record, Throwable error) {
        jdbc.update("""
                INSERT INTO saga_dead_letter
                    (operation_id, participant_service, step_name, attempt_count,
                     last_error_code, last_error_message, payload_json, quarantined_at)
                VALUES (?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE
                    attempt_count = VALUES(attempt_count),
                    last_error_code = VALUES(last_error_code),
                    last_error_message = VALUES(last_error_message),
                    payload_json = VALUES(payload_json),
                    quarantined_at = VALUES(quarantined_at)
                """,
                UUID.nameUUIDFromBytes((CONSUMER + "|" + record.topic() + "|"
                        + record.partition() + "|" + record.offset()).getBytes()).toString(),
                CONSUMER, record.topic(), (long) record.offset(),
                error == null ? null : error.getClass().getName(),
                error == null ? null : truncate(error.getMessage(), 2048),
                serialisePayload(record.value()),
                Timestamp.from(Instant.now()));
    }

    /**
     * Serialize payload của record sang chuỗi JSON. Trả về {@code "null"}
     * nếu payload null hoặc không serialize được.
     *
     * @param value giá trị payload
     * @return chuỗi JSON hoặc {@code "null"}
     */
    private String serialisePayload(Object value) {
        if (value == null) return null;
        try {
            return json.writeValueAsString(value instanceof String ? value : String.valueOf(value));
        } catch (Exception e) {
            return "null";
        }
    }

    /**
     * Cắt ngắn chuỗi nếu vượt quá {@code max} ký tự.
     *
     * @param s   chuỗi nguồn
     * @param max độ dài tối đa
     * @return chuỗi đã cắt hoặc null nếu đầu vào null
     */
    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}