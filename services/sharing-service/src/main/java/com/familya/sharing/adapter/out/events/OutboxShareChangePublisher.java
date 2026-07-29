package com.familya.sharing.adapter.out.events;

import com.familya.platform.outbox.JdbcOutboxWriter;
import com.familya.platform.outbox.OutboxWriter;
import com.familya.sharing.application.port.out.ShareChangePublisher;
import com.familya.sharing.domain.model.ShareLink;
import com.familya.platform.telemetry.PlatformMetrics;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class OutboxShareChangePublisher implements ShareChangePublisher {

    private final OutboxWriter outbox;
    private final NamedParameterJdbcTemplate jdbc;
    private final PlatformMetrics metrics;

    public OutboxShareChangePublisher(OutboxWriter outbox, NamedParameterJdbcTemplate jdbc, PlatformMetrics metrics) {
        this.outbox = outbox;
        this.jdbc = jdbc;
        this.metrics = metrics;
    }

    @Override
    public void stage(com.familya.platform.outbox.OutboxRecord record) {
        outbox.stage(record);
    }

    @Override
    public void shareLinkCreated(ShareLink link) {
        Map<String, Object> payload = basePayload(link);
        payload.put("scope", link.scope().name());
        payload.put("role", link.role().name());
        payload.put("targetId", link.targetId() == null ? null : link.targetId().toString());
        stage(link.id().toString(), link.treeId(), "ShareLinkCreated", link.version() + 1, payload);
    }

    @Override
    public void shareLinkRevoked(ShareLink link) {
        Map<String, Object> payload = basePayload(link);
        payload.put("revokedAt", link.revokedAt().toString());
        payload.put("reason", link.revocationReason());
        stage(link.id().toString(), link.treeId(), "ShareLinkRevoked", link.version() + 1, payload);
    }

    @Override
    public void projectionRebuilt(UUID treeId, long watermark) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("treeId", treeId.toString());
        payload.put("watermark", watermark);
        payload.put("eventType", "ShareProjectionRebuilt");
        payload.put("eventVersion", 1);
        payload.put("occurredAt", Instant.now().toString());
        stage(treeId.toString(), treeId, "ShareProjectionRebuilt", watermark, payload);
    }

    @Override
    @Transactional(readOnly = true)
    public List<com.familya.platform.outbox.OutboxRecord> listPending(int limit) {
        var rows = jdbc.queryForList(
                "SELECT id, aggregate_type, aggregate_id, aggregate_version, event_type, event_version, "
                        + "topic, partition_key, correlation_id, causation_id, operation_id, traceparent, "
                        + "payload_json, headers_json, occurred_at FROM outbox_record "
                        + "WHERE published_at IS NULL ORDER BY occurred_at ASC LIMIT :lim",
                new MapSqlParameterSource("lim", limit));
        return rows.stream().map(r -> new com.familya.platform.outbox.OutboxRecord(
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
                null)).toList();
    }

    @Override
    @Transactional
    public void markPublished(UUID id) {
        jdbc.update(
                "UPDATE outbox_record SET published_at = :p, locked_until = NULL WHERE id = :id",
                new MapSqlParameterSource()
                        .addValue("p", Timestamp.from(Instant.now()))
                        .addValue("id", id.toString()));
    }

    private Map<String, Object> basePayload(ShareLink link) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("treeId", link.treeId().toString());
        payload.put("shareId", link.id().toString());
        payload.put("eventType", "");
        payload.put("eventVersion", 1);
        payload.put("revision", link.revision());
        payload.put("occurredAt", link.createdAt().toString());
        return payload;
    }

    private void stage(String aggId, UUID treeId, String eventType, long version, Map<String, Object> payload) {
        JdbcOutboxWriter.Builder b = JdbcOutboxWriter.builder()
                .create("share", aggId, version, eventType, 1, "sharing.events.v1", treeId.toString(), payload);
        b.header("eventType", eventType);
        b.header("eventVersion", "1");
        b.header("treeId", treeId.toString());
        b.header("shareId", aggId);
        outbox.stage(b.build());
        metrics.outboxStaged("sharing-service", eventType);
    }
}
