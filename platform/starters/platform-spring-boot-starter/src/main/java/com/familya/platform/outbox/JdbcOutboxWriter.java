package com.familya.platform.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Adapter JDBC cho {@link OutboxWriter}.
 *
 * <p>Bảng {@code outbox_record} được tạo bởi migration Flyway của dịch vụ;
 * schema cố định như sau:</p>
 *
 * <pre>
 * CREATE TABLE outbox_record (
 *   id                 CHAR(36)     NOT NULL,
 *   aggregate_type     VARCHAR(64)  NOT NULL,
 *   aggregate_id       VARCHAR(64)  NOT NULL,
 *   aggregate_version  BIGINT       NOT NULL,
 *   event_type         VARCHAR(128) NOT NULL,
 *   event_version      INT          NOT NULL,
 *   topic              VARCHAR(128) NOT NULL,
 *   partition_key      VARCHAR(128) NOT NULL,
 *   correlation_id     CHAR(36),
 *   causation_id       CHAR(36),
 *   operation_id       CHAR(36),
 *   traceparent        VARCHAR(64),
 *   payload_json       JSON         NOT NULL,
 *   headers_json       JSON,
 *   occurred_at        TIMESTAMP(6) NOT NULL,
 *   locked_until       TIMESTAMP(6),
 *   published_at       TIMESTAMP(6),
 *   PRIMARY KEY (id),
 *   KEY ix_outbox_unpublished (published_at, locked_until)
 * );
 * </pre>
 *
 * <p>Index {@code ix_outbox_unpublished} giúp relay truy vấn nhanh các bản
 * ghi chưa publish và không bị khoá (lock).</p>
 *
 * @author Family Tree Platform Team
 */
@Component
public class JdbcOutboxWriter implements OutboxWriter {

    /** Template JDBC dùng để thực thi INSERT. */
    private final NamedParameterJdbcTemplate jdbc;

    /** ObjectMapper dùng để serialize headers thành JSON. */
    private final ObjectMapper mapper;

    /**
     * Khởi tạo writer với {@link NamedParameterJdbcTemplate} và {@link ObjectMapper}.
     *
     * @param jdbc   template JDBC
     * @param mapper mapper dùng để serialize headers
     */
    @Autowired
    public JdbcOutboxWriter(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /**
     * Stage một bản ghi outbox. Phương thức sử dụng propagation {@link Propagation#MANDATORY}
     * nghĩa là BẮT BUỘC phải nằm trong một transaction đang hoạt động — đây là
     * cơ chế đảm bảo tính nguyên tử với aggregate nghiệp vụ.
     *
     * @param r bản ghi outbox cần ghi
     * @throws IllegalStateException nếu headers không serialize được thành JSON
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void stage(OutboxRecord r) {
        try {
            // Bước 1: Thực thi INSERT với đầy đủ các trường, mapping từ record sang
            // parameter theo tên. Sử dụng INSERT đơn giản vì id là UUID sinh ngẫu nhiên,
            // không cần auto-increment.
            jdbc.update(
                    "INSERT INTO outbox_record "
                            + "(id, aggregate_type, aggregate_id, aggregate_version, event_type, event_version, "
                            + " topic, partition_key, correlation_id, causation_id, operation_id, traceparent, "
                            + " payload_json, headers_json, occurred_at) "
                            + "VALUES (:id, :at, :ai, :av, :et, :ev, :tp, :pk, :co, :ca, :op, :tr, :pl, :hd, :oa)",
                    new MapSqlParameterSource()
                            .addValue("id", r.id().toString())
                            .addValue("at", r.aggregateType())
                            .addValue("ai", r.aggregateId())
                            .addValue("av", r.aggregateVersion())
                            .addValue("et", r.eventType())
                            .addValue("ev", r.eventVersion())
                            .addValue("tp", r.topic())
                            .addValue("pk", r.partitionKey())
                            .addValue("co", r.correlationId())
                            .addValue("ca", r.causationId())
                            .addValue("op", r.operationId())
                            .addValue("tr", r.traceparent())
                            .addValue("pl", r.payloadJson())
                            // Headers được serialize thành JSON để lưu vào cột JSON.
                            .addValue("hd", mapper.writeValueAsString(r.headers()))
                            .addValue("oa", Timestamp.from(r.occurredAt())));
        } catch (JsonProcessingException e) {
            // Nếu không serialize được headers thì throw — transaction sẽ rollback.
            throw new IllegalStateException("Cannot serialize outbox headers", e);
        }
    }

    /**
     * @return một {@link Builder} mới để dựng {@link OutboxRecord} tiện lợi.
     */
    public static Builder builder() { return new Builder(); }

    /**
     * Builder tiện lợi cho trường hợp phổ biến. Dịch vụ bọc các sự kiện nghiệp vụ
     * của mình trong builder này trước khi gọi {@link OutboxWriter#stage(OutboxRecord)}.
     *
     * @author Family Tree Platform Team
     */
    public static final class Builder {
        private UUID id;
        private String aggregateType;
        private String aggregateId;
        private long aggregateVersion;
        private String eventType;
        private int eventVersion;
        private String topic;
        private String partitionKey;
        private String correlationId;
        private String causationId;
        private String operationId;
        private String traceparent;
        private Object payload;
        private final Map<String, String> headers = new HashMap<>();
        private final ObjectMapper m = new ObjectMapper();

        /**
         * Khởi tạo builder với các tham số bắt buộc của một sự kiện outbox.
         *
         * @param aggregateType     loại aggregate
         * @param aggregateId       định danh aggregate
         * @param aggregateVersion  phiên bản aggregate
         * @param eventType         loại sự kiện
         * @param eventVersion      phiên bản schema sự kiện
         * @param topic             topic Kafka đích
         * @param partitionKey      khoá phân vùng
         * @param payload           đối tượng payload (sẽ được serialize tự động)
         * @return {@link Builder} đã được cấu hình các trường bắt buộc
         */
        public static Builder create(String aggregateType, String aggregateId, long aggregateVersion,
                                     String eventType, int eventVersion, String topic, String partitionKey,
                                     Object payload) {
            return new Builder()
                    .id(UUID.randomUUID())            // Tự sinh UUID cho bản ghi
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .aggregateVersion(aggregateVersion)
                    .eventType(eventType)
                    .eventVersion(eventVersion)
                    .topic(topic)
                    .partitionKey(partitionKey)
                    .payload(payload);
        }

        /** @param v id mới */
        public Builder id(UUID v) { this.id = v; return this; }
        /** @param v aggregateType mới */
        public Builder aggregateType(String v) { this.aggregateType = v; return this; }
        /** @param v aggregateId mới */
        public Builder aggregateId(String v) { this.aggregateId = v; return this; }
        /** @param v aggregateVersion mới */
        public Builder aggregateVersion(long v) { this.aggregateVersion = v; return this; }
        /** @param v eventType mới */
        public Builder eventType(String v) { this.eventType = v; return this; }
        /** @param v eventVersion mới */
        public Builder eventVersion(int v) { this.eventVersion = v; return this; }
        /** @param v topic mới */
        public Builder topic(String v) { this.topic = v; return this; }
        /** @param v partitionKey mới */
        public Builder partitionKey(String v) { this.partitionKey = v; return this; }
        /** @param v correlationId mới */
        public Builder correlationId(String v) { this.correlationId = v; return this; }
        /** @param v causationId mới */
        public Builder causationId(String v) { this.causationId = v; return this; }
        /** @param v operationId mới */
        public Builder operationId(String v) { this.operationId = v; return this; }
        /** @param v traceparent mới */
        public Builder traceparent(String v) { this.traceparent = v; return this; }
        /** @param v payload mới (sẽ được serialize khi build) */
        public Builder payload(Object v) { this.payload = v; return this; }

        /**
         * Thêm một header tuỳ ý vào bản ghi outbox.
         *
         * @param k tên header
         * @param v giá trị header
         * @return {@link Builder} hiện tại để tiếp tục chain
         */
        public Builder header(String k, String v) { this.headers.put(k, v); return this; }

        /**
         * Dựng {@link OutboxRecord} từ các tham số đã thiết lập.
         *
         * <p>Quy trình:</p>
         * <ol>
         *   <li>Serialize payload thành JSON (string).</li>
         *   <li>Tạo {@link Map#copyOf(headers)} để đảm bảo headers là bất biến.</li>
         *   <li>Lấy thời điểm hiện tại làm {@code occurredAt}.</li>
         *   <li>Trả về {@link OutboxRecord} hoàn chỉnh.</li>
         * </ol>
         *
         * @return {@link OutboxRecord} hoàn chỉnh
         * @throws IllegalStateException nếu payload không serialize được
         */
        public OutboxRecord build() {
            try {
                return new OutboxRecord(
                        id, aggregateType, aggregateId, aggregateVersion, eventType, eventVersion,
                        topic, partitionKey, correlationId, causationId, operationId, traceparent,
                        m.writeValueAsString(payload), Map.copyOf(headers), Instant.now(), null);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
