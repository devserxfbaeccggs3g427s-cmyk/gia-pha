package com.familya.member.adapter.in.kafka;

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
 * Kho dead-letter (thư trả về không thành công) cho Saga xóa thành viên.
 * <p>Lưu trữ hai loại bản ghi:
 * <ul>
 *   <li><b>POISON</b> — bản tin Kafka không thể phân tích được (JSON lỗi, thiếu trường bắt buộc, v.v.).</li>
 *   <li><b>RETRY_EXHAUSTED</b> — bước Saga đã vượt quá số lần thử tối đa hoặc quá thời hạn cho phép.</li>
 * </ul>
 * <p>Bean này thuộc tầng adapter-in trong kiến trúc Hexagonal, được Spring quản lý vòng đời
 * thông qua {@code @Component}.
 */
@Component
public class DeleteMemberSagaDeadLetterStore {
    /** Mã định danh consumer khi ghi dead-letter — dùng để truy vết theo consumer. */
    static final String CONSUMER = "member-service.delete-member";
    /** Loại Saga gắn với dead-letter — cố định là {@code delete-member}. */
    static final String SAGA_TYPE = "delete-member";
    /** Loại nguồn cho bản ghi "độc" (poison) — không phân tích được. */
    static final String SOURCE_KIND_POISON = "POISON";
    /** Loại nguồn cho bản ghi đã hết lượt thử lại (retry exhausted). */
    static final String SOURCE_KIND_RETRY_EXHAUSTED = "RETRY_EXHAUSTED";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    /**
     * Khởi tạo kho dead-letter với {@link JdbcTemplate} để ghi CSDL và {@link ObjectMapper} để tuần tự hóa payload an toàn.
     *
     * @param jdbc template JDBC dùng để chèn bản ghi dead-letter
     * @param json mapper JSON dùng để tuần tự hóa payload (có thể bị che để tránh lộ dữ liệu nhạy cảm)
     */
    public DeleteMemberSagaDeadLetterStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * Ghi một bản ghi poison khi bản tin Kafka không phân tích được.
     *
     * @param record bản tin Kafka gốc gây lỗi — dùng để lấy topic/partition/offset làm khóa truy vết
     * @param error  ngoại lệ phát sinh trong quá trình giải mã/kiểm tra
     */
    public void savePoison(ConsumerRecord<String, Object> record, Throwable error) {
        // Tạo khóa truy vết duy nhất từ (topic, partition, offset) để dễ tra cứu khi điều tra sự cố
        String sourceKey = record.topic() + ":" + record.partition() + ":" + record.offset();
        // Các trường về Saga (operationId, participantService, stepCode, attemptCount) đều null
        // vì bản tin poison không đến được giai đoạn nhận diện bước
        insert(SOURCE_KIND_POISON, sourceKey, null, null, null, null,
                record, record.value(), error);
    }

    /**
     * Ghi bản ghi khi một bước Saga đã cạn lượng thử lại.
     *
     * @param operationId       mã operationId của Saga
     * @param participantService tên dịch vụ tham gia (ví dụ: {@code relationship-service})
     * @param stepCode          mã bước bị thất bại
     * @param attemptCount      số lần đã thử trước khi đánh dấu chết
     * @param error             ngoại lệ cuối cùng gây thất bại
     */
    public void saveRetryExhausted(UUID operationId, String participantService, String stepCode,
                                   int attemptCount, Throwable error) {
        // Khóa truy vết gồm (operationId, participant, step, attempt) để phân biệt các lần chết khác nhau
        String sourceKey = operationId + "|" + participantService + "|" + stepCode + "|" + attemptCount;
        // Không có record Kafka gốc vì đây là dead-letter nội bộ sinh ra từ deadline scanner
        insert(SOURCE_KIND_RETRY_EXHAUSTED, sourceKey,
                operationId, participantService, stepCode, attemptCount,
                null, null, error);
    }

    /**
     * Thực hiện câu lệnh INSERT ... ON DUPLICATE KEY UPDATE vào bảng {@code member_saga_dead_letter}.
     *
     * @param sourceKind        loại nguồn ({@link #SOURCE_KIND_POISON} hoặc {@link #SOURCE_KIND_RETRY_EXHAUSTED})
     * @param sourceKey         khóa truy vết duy nhất theo loại nguồn
     * @param operationId       mã operationId của Saga (có thể null nếu là poison)
     * @param participantService dịch vụ tham gia (null nếu là poison)
     * @param stepCode          mã bước (null nếu là poison)
     * @param attemptCount      số lần thử (null nếu là poison)
     * @param record            bản tin Kafka gốc (null với retry-exhausted)
     * @param payload           payload đầu vào cần tuần tự hóa an toàn
     * @param error             ngoại lệ liên quan (có thể null)
     */
    private void insert(String sourceKind, String sourceKey,
                        UUID operationId, String participantService, String stepCode,
                        Integer attemptCount,
                        ConsumerRecord<String, Object> record,
                        Object payload, Throwable error) {
        jdbc.update("""
                INSERT INTO member_saga_dead_letter
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
     * Tuần tự hóa payload một cách an toàn (không chứa dữ liệu nhạy cảm) để lưu xuống dead-letter.
     *
     * <p>Mục đích: bản ghi dead-letter phục vụ điều tra sự cố, KHÔNG được chứa dữ liệu thực
     * của người dùng. Vì vậy hàm này ghi nhận lại tên class và cờ {@code redacted} thay vì
     * nội dung thực.
     *
     * @param value giá trị payload đầu vào (có thể là byte[] hoặc object bất kỳ)
     * @return chuỗi JSON biểu thị payload đã được che, hoặc {@code null} nếu đầu vào là null
     */
    private String serialisePayload(Object value) {
        if (value == null) return null;
        if (value instanceof byte[] bytes) {
            return "{\"valueClass\":\"[B\",\"length\":" + bytes.length + "}";
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        safe.put("valueClass", value.getClass().getName());
        safe.put("redacted", true);
        try {
            return json.writeValueAsString(safe);
        } catch (JsonProcessingException e) {
            return "{\"redacted\":true}";
        }
    }

    /**
     * Cắt ngắn chuỗi nếu vượt quá độ dài cho phép — bảo vệ cột CSDL khỏi giá trị quá lớn.
     *
     * @param s   chuỗi đầu vào (có thể null)
     * @param max độ dài tối đa cho phép
     * @return chuỗi đã cắt, hoặc {@code null} nếu đầu vào là null
     */
    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}