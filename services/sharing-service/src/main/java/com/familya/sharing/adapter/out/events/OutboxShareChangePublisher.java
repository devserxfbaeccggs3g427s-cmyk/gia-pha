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

/**
 * Adapter hiện thực {@link ShareChangePublisher} sử dụng cơ chế
 * <b>Transactional Outbox</b> với bảng {@code outbox_record}.
 * <p>
 * Mọi sự kiện thay đổi của share link sẽ được ghi vào bảng outbox trong cùng
 * transaction với thay đổi dữ liệu, đảm bảo tính nhất quán. Một tiến trình
 * nền (do platform cung cấp) sẽ đọc các bản ghi này và phát hành lên Kafka.
 */
@Component
public class OutboxShareChangePublisher implements ShareChangePublisher {

    private final OutboxWriter outbox;
    private final NamedParameterJdbcTemplate jdbc;
    private final PlatformMetrics metrics;

    /**
     * Khởi tạo adapter.
     *
     * @param outbox  cổng ghi outbox của platform.
     * @param jdbc    template JDBC (dùng cho {@code listPending}/{@code markPublished}).
     * @param metrics bộ thu thập metric.
     */
    public OutboxShareChangePublisher(OutboxWriter outbox, NamedParameterJdbcTemplate jdbc, PlatformMetrics metrics) {
        this.outbox = outbox;
        this.jdbc = jdbc;
        this.metrics = metrics;
    }

    /**
     * Ghi trực tiếp một bản ghi outbox đã được tạo sẵn.
     *
     * @param record bản ghi {@link com.familya.platform.outbox.OutboxRecord}.
     */
    @Override
    public void stage(com.familya.platform.outbox.OutboxRecord record) {
        outbox.stage(record);
    }

    /**
     * Phát hành sự kiện {@code ShareLinkCreated}.
     * <p>
     * Payload bao gồm thông tin cơ bản của liên kết cộng thêm scope/role/targetId.
     *
     * @param link liên kết vừa được tạo.
     */
    @Override
    public void shareLinkCreated(ShareLink link) {
        Map<String, Object> payload = basePayload(link);
        payload.put("scope", link.scope().name());
        payload.put("role", link.role().name());
        payload.put("targetId", link.targetId() == null ? null : link.targetId().toString());
        // version + 1 là phiên bản nghiệp vụ sau sự kiện này.
        stage(link.id().toString(), link.treeId(), "ShareLinkCreated", link.version() + 1, payload);
    }

    /**
     * Phát hành sự kiện {@code ShareLinkRevoked}.
     *
     * @param link liên kết vừa bị thu hồi.
     */
    @Override
    public void shareLinkRevoked(ShareLink link) {
        Map<String, Object> payload = basePayload(link);
        payload.put("revokedAt", link.revokedAt().toString());
        payload.put("reason", link.revocationReason());
        stage(link.id().toString(), link.treeId(), "ShareLinkRevoked", link.version() + 1, payload);
    }

    /**
     * Phát hành sự kiện {@code ShareProjectionRebuilt} khi một projection công
     * khai được tái tạo.
     *
     * @param treeId   định danh cây gia phả.
     * @param watermark phiên bản watermark mới.
     */
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

    /**
     * Lấy các bản ghi outbox đang chờ xuất bản (chưa có {@code published_at}),
     * sắp xếp theo thời điểm phát sinh tăng dần.
     * <p>
     * Thực thi trong transaction chỉ-đọc &mdash; đảm bảo không lock dữ liệu.
     *
     * @param limit số bản ghi tối đa.
     * @return danh sách {@link com.familya.platform.outbox.OutboxRecord}.
     */
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

    /**
     * Đánh dấu một bản ghi outbox là đã được xuất bản thành công &mdash; cập
     * nhật {@code published_at} và giải phóng khóa.
     *
     * @param id định danh bản ghi outbox.
     */
    @Override
    @Transactional
    public void markPublished(UUID id) {
        jdbc.update(
                "UPDATE outbox_record SET published_at = :p, locked_until = NULL WHERE id = :id",
                new MapSqlParameterSource()
                        .addValue("p", Timestamp.from(Instant.now()))
                        .addValue("id", id.toString()));
    }

    /**
     * Xây dựng payload cơ sở cho các sự kiện liên quan đến share link.
     *
     * @param link liên kết chia sẻ.
     * @return {@code LinkedHashMap} chứa các trường cơ bản.
     */
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

    /**
     * Stage một bản ghi outbox qua {@link JdbcOutboxWriter.Builder} với các
     * header chuẩn. Đồng thời báo cáo metric.
     *
     * @param aggId    định danh aggregate.
     * @param treeId   định danh cây gia phả.
     * @param eventType loại sự kiện.
     * @param version  phiên bản aggregate.
     * @param payload  payload sự kiện.
     */
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