package com.familya.treeaccess.adapter.in.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Kho lưu trữ dead-letter (DLQ) cho các reply Saga delete-tree mà service
 * này không xử lý được (do payload lỗi, retry cạn kiệt,...).
 *
 * <p>Mỗi bản ghi được lưu vào bảng {@code tree_saga_dead_letter} với hai
 * loại nguồn: {@link #SOURCE_KIND_POISON} (không giải mã được) và
 * {@link #SOURCE_KIND_RETRY_EXHAUSTED} (đã retry hết nhưng vẫn thất bại).</p>
 *
 * <p>Truy vấn các bản ghi này giúp vận hành viên điều tra nguyên nhân và
 * quyết định chạy lại Saga thủ công.</p>
 */
@Component
public class DeleteTreeSagaDeadLetterStore {
    /** Tên consumer phục vụ cho cột {@code consumer} trong DLQ. */
    static final String CONSUMER = "tree-access-service.delete-tree";
    /** Loại Saga gắn với các bản ghi dead-letter. */
    static final String SAGA_TYPE = "delete-tree";
    /** Loại nguồn: message không giải mã được (poison). */
    static final String SOURCE_KIND_POISON = "POISON";
    /** Loại nguồn: đã retry cạn kiệt nhưng vẫn thất bại. */
    static final String SOURCE_KIND_RETRY_EXHAUSTED = "RETRY_EXHAUSTED";

    /** JdbcTemplate để chèn/cập nhật bản ghi DLQ. */
    private final JdbcTemplate jdbc;

    /** ObjectMapper dùng khi cần serialize payload ở dạng JSON an toàn. */
    private final ObjectMapper json;

    /**
     * Khởi tạo store DLQ.
     *
     * @param jdbc template JDBC để ghi vào bảng dead-letter
     * @param json bộ mapper JSON để xử lý payload
     */
    public DeleteTreeSagaDeadLetterStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * Lưu một bản ghi DLQ dạng POISON — Kafka record không giải mã được.
     * {@code sourceKey} ghép từ topic, partition và offset để đảm bảo duy nhất.
     *
     * @param record bản ghi Kafka bị lỗi
     * @param error  ngoại lệ gốc dẫn đến việc đưa vào DLQ
     */
    public void savePoison(ConsumerRecord<String, Object> record, Throwable error) {
        // Khoá nguồn: topic:partition:offset để có thể tái dựng vị trí bản tin trong topic.
        String sourceKey = record.topic() + ":" + record.partition() + ":" + record.offset();
        insert(SOURCE_KIND_POISON, sourceKey, null, null, null, null,
                record, record.value(), error);
    }

    /**
     * Lưu bản ghi DLQ khi Saga retry đã cạn kiệt nhưng vẫn thất bại.
     *
     * @param operationId       mã thao tác Saga tương ứng
     * @param participantService tên bounded-context tham gia
     * @param stepCode           mã bước trong Saga
     * @param attemptCount       số lần thử đã thực hiện
     * @param error              ngoại lệ cuối cùng
     */
    public void saveRetryExhausted(UUID operationId, String participantService, String stepCode,
                                   int attemptCount, Throwable error) {
        // Khoá nguồn kết hợp operationId + service + step + lần thử để truy vết đầy đủ.
        String sourceKey = operationId + "|" + participantService + "|" + stepCode + "|" + attemptCount;
        insert(SOURCE_KIND_RETRY_EXHAUSTED, sourceKey,
                operationId, participantService, stepCode, attemptCount,
                null, null, error);
    }

    /**
     * Thực hiện câu lệnh INSERT … ON DUPLICATE KEY UPDATE vào bảng DLQ.
     *
     * @param sourceKind         loại nguồn ({@link #SOURCE_KIND_POISON} / {@link #SOURCE_KIND_RETRY_EXHAUSTED})
     * @param sourceKey          khoá tra cứu duy nhất theo loại nguồn
     * @param operationId        mã thao tác Saga (có thể null với POISON)
     * @param participantService tên service tham gia (có thể null với POISON)
     * @param stepCode           mã bước (có thể null với POISON)
     * @param attemptCount       số lần thử (có thể null với POISON)
     * @param record             bản ghi Kafka gốc hoặc null
     * @param payload            payload có thể đã được serialize an toàn
     * @param error              ngoại lệ xảy ra
     */
    private void insert(String sourceKind, String sourceKey,
                        UUID operationId, String participantService, String stepCode,
                        Integer attemptCount,
                        ConsumerRecord<String, Object> record,
                        Object payload, Throwable error) {
        jdbc.update("""
                INSERT INTO tree_saga_dead_letter
                    (id, consumer, source_kind, source_key,
                     operation_id, saga_type, participant_service, step_code,
                     attempt_count, topic, partition_id, offset_value, payload_text,
                     error_class, error_message, occurred_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE
                    operation_id = VALUES(operation_id),
                    saga_type = VALUES(saga_type),
                    participant_service = VALUES(participant_service),
                    step_code = VALUES(step_code),
                    attempt_count = VALUES(attempt_count),
                    payload_text = VALUES(payload_text),
                    error_class = VALUES(error_class),
                    error_message = VALUES(error_message),
                    occurred_at = VALUES(occurred_at)
                """,
                UUID.randomUUID().toString(), CONSUMER, sourceKind, sourceKey,
                operationId == null ? null : operationId.toString(), SAGA_TYPE,
                participantService, stepCode, attemptCount,
                record == null ? "" : record.topic(),
                record == null ? -1 : record.partition(),
                record == null ? -1L : record.offset(),
                serialisePayload(payload),
                error == null ? null : error.getClass().getName(),
                error == null ? null : truncate(error.getMessage(), 2048),
                Timestamp.from(Instant.now()));
    }

    /**
     * Serialize payload thành chuỗi JSON an toàn để lưu DLQ mà không lộ dữ liệu
     * nhạy cảm. Với mảng byte chỉ ghi nhận độ dài; với các kiểu khác ghi nhận
     * tên lớp và cờ {@code redacted=true}.
     *
     * @param value payload gốc
     * @return chuỗi JSON đã được "làm sạch" hoặc {@code null} nếu đầu vào null
     */
    private String serialisePayload(Object value) {
        if (value == null) return null;
        if (value instanceof byte[] bytes) {
            // Không ghi trực tiếp nội dung byte[] vì có thể chứa PII hoặc nhị phân lớn.
            return "{\"valueClass\":\"[B\",\"length\":" + bytes.length + "}";
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        safe.put("valueClass", value.getClass().getName());
        safe.put("redacted", true);
        try {
            return json.writeValueAsString(safe);
        } catch (JsonProcessingException e) {
            // Phòng trường hợp mapper thất bại — vẫn trả về một payload tối thiểu.
            return "{\"redacted\":true}";
        }
    }

    /**
     * Cắt chuỗi nếu dài quá giới hạn để tránh tràn cột DLQ.
     *
     * @param s   chuỗi đầu vào (có thể null)
     * @param max độ dài tối đa cho phép
     * @return chuỗi đã được cắt hoặc {@code null}
     */
    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}