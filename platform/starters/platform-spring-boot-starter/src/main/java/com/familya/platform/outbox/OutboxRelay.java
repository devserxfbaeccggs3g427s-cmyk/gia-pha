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
 * Polling relay that reads committed outbox rows, publishes them to
 * Kafka with the required metadata headers, and marks them as
 * published. The relay is at-least-once; downstream consumers MUST
 * dedupe using the {@code event_id} header. A claim row is written so
 * multiple replicas do not publish the same record.
 *
 * <p>Disable with {@code familya.outbox.relay.enabled=false} in tests
 * that exercise the producer directly.</p>
 */
@Component
@ConditionalOnProperty(prefix = "familya.outbox.relay", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 100;
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    private final NamedParameterJdbcTemplate jdbc;
    private final KafkaTemplate<String, Object> kafka;
    private final String serviceName;

    @Value("${spring.application.name:unknown}")
    private String configuredServiceName;

    public OutboxRelay(NamedParameterJdbcTemplate jdbc, KafkaTemplate<String, Object> kafka) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.serviceName = null;
    }

    @Scheduled(fixedDelayString = "${familya.outbox.relay.interval-ms:500}")
    public void publish() {
        for (int i = 0; i < BATCH_SIZE; i++) {
            if (!publishOne()) {
                break;
            }
        }
    }

    private boolean publishOne() {
        Instant now = Instant.now();
        Instant lockDeadline = now.plus(LOCK_TTL);
        List<Map<String, Object>> candidates = jdbc.queryForList(
                "SELECT id FROM outbox_record "
                        + "WHERE published_at IS NULL AND (locked_until IS NULL OR locked_until < :now) "
                        + "ORDER BY occurred_at LIMIT 1 FOR UPDATE SKIP LOCKED",
                new MapSqlParameterSource("now", Timestamp.from(now)));
        if (candidates.isEmpty()) {
            return false;
        }
        String id = (String) candidates.get(0).get("id");
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
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT id, aggregate_type, aggregate_id, aggregate_version, event_type, event_version, "
                        + "topic, partition_key, correlation_id, causation_id, operation_id, traceparent, "
                        + "payload_json, headers_json "
                        + "FROM outbox_record WHERE id = :id",
                new MapSqlParameterSource("id", id));
        ProducerRecord<String, Object> pr = new ProducerRecord<>(
                (String) row.get("topic"),
                (String) row.get("partition_key"),
                (String) row.get("payload_json"));
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

    private static void addHeader(ProducerRecord<String, Object> pr, String name, String value) {
        if (value != null) {
            pr.headers().add(new RecordHeader(name, value.getBytes(StandardCharsets.UTF_8)));
        }
    }

    private static void copyIfPresent(ProducerRecord<String, Object> pr, String name, Map<String, Object> row) {
        Object v = row.get(name);
        if (v != null) {
            addHeader(pr, name, v.toString());
        }
    }
}
