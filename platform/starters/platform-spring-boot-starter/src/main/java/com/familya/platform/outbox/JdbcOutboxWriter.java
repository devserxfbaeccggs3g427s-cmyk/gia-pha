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
import java.util.Map;
import java.util.UUID;

/**
 * JDBC adapter for {@link OutboxWriter}. The table is created by the
 * service's Flyway migration; the schema is fixed:
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
 */
@Component
public class JdbcOutboxWriter implements OutboxWriter {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    @Autowired
    public JdbcOutboxWriter(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void stage(OutboxRecord r) {
        try {
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
                            .addValue("hd", mapper.writeValueAsString(r.headers()))
                            .addValue("oa", Timestamp.from(r.occurredAt())));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize outbox headers", e);
        }
    }

    public static OutboxRecord.Builder builder() { return OutboxRecord.builder(); }

    /**
     * Convenience record-builder for the common case. Services wrap
     * their domain events in this builder before handing them to
     * {@link OutboxWriter#stage(OutboxRecord)}.
     */
    public static final class Builder {
        private final OutboxRecord r;
        private final ObjectMapper m = new ObjectMapper();

        private Builder(UUID id, String aggregateType, String aggregateId, long aggregateVersion,
                        String eventType, int eventVersion, String topic, String partitionKey,
                        Object payload, Map<String, String> headers) {
            try {
                this.r = new OutboxRecord(
                        id, aggregateType, aggregateId, aggregateVersion, eventType, eventVersion,
                        topic, partitionKey, null, null, null, null,
                        m.writeValueAsString(payload), headers, java.time.Instant.now(), null);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }

        public static Builder create(String aggregateType, String aggregateId, long aggregateVersion,
                                     String eventType, int eventVersion, String topic, String partitionKey,
                                     Object payload) {
            return new Builder(UUID.randomUUID(), aggregateType, aggregateId, aggregateVersion,
                    eventType, eventVersion, topic, partitionKey, payload, Map.of());
        }

        public Builder correlationId(String v) { return with(r -> r.correlationId = v); }
        public Builder causationId(String v)   { return with(r -> r.causationId = v); }
        public Builder operationId(String v)   { return with(r -> r.operationId = v); }
        public Builder traceparent(String v)   { return with(r -> r.traceparent = v); }
        public Builder header(String k, String v) { return with(r -> r.headers.put(k, v)); }
        public OutboxRecord build() { return r; }

        private Builder with(java.util.function.Consumer<OutboxRecord> mut) { mut.accept(r); return this; }
    }
}
