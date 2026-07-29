package com.familya.media.adapter.out.events;

import com.familya.media.application.port.out.MediaOutbox;
import com.familya.platform.outbox.OutboxRecord;
import com.familya.platform.outbox.OutboxWriter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class JdbcMediaOutbox implements MediaOutbox {

    private final NamedParameterJdbcTemplate jdbc;
    private final OutboxWriter writer;

    public JdbcMediaOutbox(NamedParameterJdbcTemplate jdbc, OutboxWriter writer) {
        this.jdbc = jdbc;
        this.writer = writer;
    }

    @Override
    public void stage(OutboxRecord record) {
        writer.stage(record);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OutboxRecord> listPending(int limit) {
        var rows = jdbc.queryForList(
                "SELECT id, aggregate_type, aggregate_id, aggregate_version, event_type, event_version, "
                        + "topic, partition_key, correlation_id, causation_id, operation_id, traceparent, "
                        + "payload_json, headers_json, occurred_at FROM outbox_record "
                        + "WHERE published_at IS NULL ORDER BY occurred_at ASC LIMIT :lim",
                new MapSqlParameterSource("lim", limit));
        return rows.stream().map(this::fromRow).toList();
    }

    private OutboxRecord fromRow(java.util.Map<String, Object> r) {
        return new OutboxRecord(
                UUID.fromString((String) r.get("id")),
                (String) r.get("aggregate_type"),
                (String) r.get("aggregate_id"),
                ((Number) r.get("aggregate_version")).longValue(),
                (String) r.get("event_type"),
                ((Number) r.get("event_version")).intValue(),
                (String) r.get("topic"),
                (String) r.get("partition_key"),
                (String) r.get("correlation_id"),
                (String) r.get("causation_id"),
                (String) r.get("operation_id"),
                (String) r.get("traceparent"),
                (String) r.get("payload_json"),
                java.util.Map.of(),
                ((Timestamp) r.get("occurred_at")).toInstant(),
                null);
    }
}
