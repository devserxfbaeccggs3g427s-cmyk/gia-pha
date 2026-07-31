package com.familya.platform.outbox;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Relay polling đọc các bản ghi outbox đã commit, publish chúng lên Kafka kèm
 * các header metadata bắt buộc, và đánh dấu đã publish.
 *
 * <p><b>Tính chất đảm bảo:</b> Relay là at-least-once — nếu quá trình publish
 * thất bại giữa chừng, bản ghi sẽ được thử lại ở chu kỳ kế tiếp. Consumer
 * downstream BẮT BUỘC phải dedupe bằng header {@code event_id} (thông qua
 * {@link com.familya.platform.inbox.InboxStore}).</p>
 *
 * <p><b>Cơ chế khoá:</b> Mỗi bản ghi được khoá bằng {@code locked_until}
 * với TTL {@value #LOCK_TTL} giây — nhiều replica relay có thể chạy đồng
 * thời mà không publish trùng bản ghi nhờ {@code FOR UPDATE SKIP LOCKED}.</p>
 *
 * <p><b>Cách tắt:</b> Có thể tắt relay bằng cách đặt
 * {@code familya.outbox.relay.enabled=false} — hữu ích cho các test tích hợp
 * muốn kiểm tra producer trực tiếp.</p>
 *
 * @author Family Tree Platform Team
 */
@Component
@ConditionalOnProperty(prefix = "familya.outbox.relay", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    /** Logger để ghi nhận các sự kiện publish. */
    private static final Logger LOG = LoggerFactory.getLogger(OutboxRelay.class);

    /** Số bản ghi tối đa xử lý trong một chu kỳ poll. */
    private static final int BATCH_SIZE = 100;

    /** Thời gian tồn tại của khoá trên bản ghi outbox. */
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    /** Template JDBC dùng để đọc và cập nhật bảng outbox. */
    private final NamedParameterJdbcTemplate jdbc;

    /** Template Kafka dùng để publish message. */
    private final KafkaTemplate<String, Object> kafka;

    /** Tên dịch vụ hiện tại (không được sử dụng trong logic chính, giữ để mở rộng). */
    private final String serviceName;

    /** Tên dịch vụ được cấu hình qua {@code spring.application.name}. */
    @Value("${spring.application.name:unknown}")
    private String configuredServiceName;

    /**
     * Khởi tạo relay với JDBC và Kafka template.
     *
     * @param jdbc   template JDBC
     * @param kafka  template Kafka
     */
    public OutboxRelay(NamedParameterJdbcTemplate jdbc, KafkaTemplate<String, Object> kafka) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.serviceName = null;
    }

    /**
     * Vòng lặp polling chính. Được kích hoạt định kỳ theo
     * {@code familya.outbox.relay.interval-ms} (mặc định 500ms).
     *
     * <p>Trong mỗi chu kỳ, relay cố gắng xử lý tối đa {@value #BATCH_SIZE}
     * bản ghi. Vòng lặp dừng sớm khi không còn bản ghi nào để xử lý.</p>
     */
    @Scheduled(fixedDelayString = "${familya.outbox.relay.interval-ms:500}")
    public void publish() {
        // Bước 1: Thử publish từng bản ghi một cho đến khi đạt BATCH_SIZE hoặc
        // không còn bản ghi nào khả dụng.
        for (int i = 0; i < BATCH_SIZE; i++) {
            if (!publishOne()) {
                break;
            }
        }
    }

    /**
     * Publish một bản ghi outbox duy nhất. Phương thức này thực hiện toàn bộ
     * chuỗi: chọn bản ghi, khoá, đọc chi tiết, publish tới Kafka, đánh dấu.
     *
     * @return {@code true} nếu đã xử lý (kể cả thất bại) một bản ghi;
     *         {@code false} nếu không còn bản ghi nào để xử lý
     */
    private boolean publishOne() {
        Instant now = Instant.now();
        Instant lockDeadline = now.plus(LOCK_TTL);

        // Bước 1: Chọn một bản ghi chưa publish và chưa bị khoá (hoặc khoá đã hết hạn).
        // SELECT ... FOR UPDATE SKIP LOCKED cho phép nhiều replica relay chạy song song
        // mà không xung đột: bản ghi đang bị khoá bởi transaction khác sẽ bị bỏ qua.
        List<Map<String, Object>> candidates = jdbc.queryForList(
                "SELECT id FROM outbox_record "
                        + "WHERE published_at IS NULL AND (locked_until IS NULL OR locked_until < :now) "
                        + "ORDER BY occurred_at LIMIT 1 FOR UPDATE SKIP LOCKED",
                new MapSqlParameterSource("now", Timestamp.from(now)));
        if (candidates.isEmpty()) {
            return false;
        }
        String id = (String) candidates.get(0).get("id");

        // Bước 2: Khoá bản ghi bằng cách cập nhật locked_until với điều kiện
        // chưa publish và khoá cũ đã hết hạn. Nếu update không ảnh hưởng hàng nào
        // (do điều kiện không khớp), trả về true để vòng lặp ngoài tiếp tục thử
        // với bản ghi khác.
        int claimed = jdbc.update(
                "UPDATE outbox_record SET locked_until = :l WHERE id = :id AND published_at IS NULL "
                        + "AND (locked_until IS NULL OR locked_until < :now)",
                new MapSqlParameterSource()
                        .addValue("l", Timestamp.from(lockDeadline))
                        .addValue("id", id)
                        .addValue("now", Timestamp.from(now)));
        if (claimed == 0) {
            return true;
        }

        // Bước 3: Đọc toàn bộ nội dung bản ghi đã khoá.
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT id, aggregate_type, aggregate_id, aggregate_version, event_type, event_version, "
                        + "topic, partition_key, correlation_id, causation_id, operation_id, traceparent, "
                        + "payload_json, headers_json "
                        + "FROM outbox_record WHERE id = :id",
                new MapSqlParameterSource("id", id));

        // Bước 4: Dựng ProducerRecord với topic, partition key và payload.
        ProducerRecord<String, Object> pr = new ProducerRecord<>(
                (String) row.get("topic"),
                (String) row.get("partition_key"),
                (String) row.get("payload_json"));

        // Bước 5: Gắn các header metadata bắt buộc theo chuẩn nền tảng.
        // event_id là header quan trọng nhất — consumer dùng nó để dedupe.
        addHeader(pr, "event_id", id);
        addHeader(pr, "event_type", (String) row.get("event_type"));
        addHeader(pr, "event_version", String.valueOf(row.get("event_version")));
        addHeader(pr, "aggregate_type", (String) row.get("aggregate_type"));
        addHeader(pr, "aggregate_id", (String) row.get("aggregate_id"));
        addHeader(pr, "aggregate_version", String.valueOf(row.get("aggregate_version")));
        addHeader(pr, "schema_version", String.valueOf(row.get("event_version")));
        addHeader(pr, "service", configuredServiceName);
        copyIfPresent(pr, "correlation_id", row);
        copyIfPresent(pr, "causation_id", row);
        copyIfPresent(pr, "operation_id", row);
        copyIfPresent(pr, "traceparent", row);

        // Bước 6: Publish đồng bộ (đợi ack) để đảm bảo message đã tới broker
        // trước khi đánh dấu published. Nếu lỗi, log và KHÔNG đánh dấu published —
        // chu kỳ kế tiếp sẽ thử lại nhờ TTL của khoá.
        try {
            kafka.send(pr).get();
            jdbc.update("UPDATE outbox_record SET published_at = :p, locked_until = NULL WHERE id = :id",
                    new MapSqlParameterSource()
                            .addValue("p", Timestamp.from(Instant.now()))
                            .addValue("id", id));
        } catch (Exception e) {
            LOG.error("Outbox publish failed id={} topic={}", id, row.get("topic"), e);
        }
        return true;
    }

    /**
     * Thêm một header vào {@link ProducerRecord} nếu giá trị khác null.
     *
     * @param pr    ProducerRecord đích
     * @param name  tên header
     * @param value giá trị header (sẽ được encode UTF-8)
     */
    private static void addHeader(ProducerRecord<String, Object> pr, String name, String value) {
        if (value != null) {
            pr.headers().add(new RecordHeader(name, value.getBytes(StandardCharsets.UTF_8)));
        }
    }

    /**
     * Sao chép một trường từ Map (đại diện cho một hàng JDBC) sang header nếu giá trị tồn tại.
     *
     * @param pr   ProducerRecord đích
     * @param name tên trường trong Map và tên header
     * @param row  Map chứa dữ liệu hàng
     */
    private static void copyIfPresent(ProducerRecord<String, Object> pr, String name, Map<String, Object> row) {
        Object v = row.get(name);
        if (v != null) {
            addHeader(pr, name, v.toString());
        }
    }
}
