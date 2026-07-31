package com.familya.sharing.adapter.in.kafka;

import com.familya.platform.inbox.InboxStore;
import com.familya.platform.projection.ReplayLedger;
import com.familya.platform.telemetry.PlatformMetrics;
import com.familya.sharing.application.port.out.AllowlistedProjectionRepository;
import com.familya.sharing.application.port.out.AllowlistedProjectionRepository.ShareScope;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Consumer Kafka phục vụ cập nhật các projection phục vụ cho sharing-service.
 * <p>
 * Listener này đăng ký nhiều {@code @KafkaListener} cho các topic khác nhau,
 * mỗi topic đại diện cho một domain (membership, member, media, event,
 * relationship). Với mỗi bản ghi:
 * <ol>
 *     <li>Chống xử lý trùng thông qua {@link InboxStore}.</li>
 *     <li>Trích xuất các header tiêu chuẩn ({@code event_type}, {@code treeId},
 *         {@code revision}, {@code epoch}, v.v.).</li>
 *     <li>Cập nhật projection allowlist và bảng {@code authorization_projection}
 *         hoặc các bảng projection tương ứng.</li>
 *     <li>Cập nhật {@code replay_ledger} để theo dõi tiến độ tiêu thụ.</li>
 *     <li>Báo cáo metric cho platform.</li>
 * </ol>
 */
@Component
public class SharingProjectionConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(SharingProjectionConsumer.class);

    private final InboxStore inbox;
    private final NamedParameterJdbcTemplate jdbc;
    private final AllowlistedProjectionRepository projections;
    private final PlatformMetrics metrics;

    /**
     * Khởi tạo consumer với các phụ thuộc.
     *
     * @param inbox       kho lưu trữ inbox dùng để chống xử lý trùng.
     * @param jdbc        template JDBC chia sẻ.
     * @param projections cổng lưu trữ projection allowlist.
     * @param metrics     bộ thu thập metric.
     */
    public SharingProjectionConsumer(InboxStore inbox, NamedParameterJdbcTemplate jdbc,
                                     AllowlistedProjectionRepository projections, PlatformMetrics metrics) {
        this.inbox = inbox;
        this.jdbc = jdbc;
        this.projections = projections;
        this.metrics = metrics;
    }

    /**
     * Xử lý các bản ghi từ topic {@code tree.memberships.v1} &mdash; cập nhật
     * {@code authorization_projection} cho phép sharing-service đưa ra quyết
     * định phân quyền dựa trên role người dùng trong cây.
     *
     * @param records tập các bản ghi Kafka được giao trong một lần poll.
     */
    @KafkaListener(topics = "tree.memberships.v1", groupId = "${spring.application.name}")
    public void onMembership(ConsumerRecords<String, Object> records) {
        for (ConsumerRecord<String, Object> r : records) {
            try {
                // Bước 1: Kiểm tra trùng lặp; bỏ qua nếu đã xử lý.
                if (!shouldProcess(r, "tree.memberships.v1")) continue;

                // Bước 2: Trích xuất thông tin từ header.
                String eventType = headerString(r, "event_type");
                long aggRev = parseLong(headerString(r, "revision"), 0L);
                long epoch = parseLong(headerString(r, "epoch"), 0L);
                UUID treeId = parseUuid(headerString(r, "treeId"));
                UUID userId = parseUuid(headerString(r, "userId"));
                String role = headerString(r, "role");
                // Sự kiện MembershipRevoked đánh dấu mất quyền.
                boolean revoked = "MembershipRevoked".equals(eventType);

                // Bước 3: Upsert vào bảng authorization_projection.
                // Dùng ON DUPLICATE KEY UPDATE để idempotent &mdash; tránh xử lý trùng dù inbox miss.
                Instant now = Instant.now();
                jdbc.update(
                        "INSERT INTO authorization_projection (tree_id, user_id, role, revision, epoch, granted_at, revoked, source_event_id, last_updated_at) "
                                + "VALUES (:t, :u, :role, :rev, :epoch, :at, :revoked, :src, :upd) "
                                + "ON DUPLICATE KEY UPDATE role = VALUES(role), revision = VALUES(revision), epoch = VALUES(epoch), "
                                + "revoked = VALUES(revoked), source_event_id = VALUES(source_event_id), last_updated_at = VALUES(last_updated_at)",
                        new MapSqlParameterSource()
                                .addValue("t", treeId.toString())
                                .addValue("u", userId.toString())
                                .addValue("role", revoked ? null : role)
                                .addValue("rev", aggRev)
                                .addValue("epoch", epoch)
                                .addValue("at", Timestamp.from(now))
                                .addValue("revoked", revoked)
                                .addValue("src", headerString(r, "event_id"))
                                .addValue("upd", Timestamp.from(now)));

                // Bước 4: Cập nhật replay ledger & metric.
                recordReplay(treeId, "tree.memberships.v1", r.partition(), r.offset(), aggRev, epoch, now);
                metrics.consumerProcessed("sharing-service", "membership");
            } catch (Exception ex) {
                // Lỗi &mdash; ghi log, không ném để không poison-pill cả batch.
                LOG.error("Failed to process membership offset={}", r.offset(), ex);
            }
        }
    }

    /**
     * Xử lý các bản ghi từ topic {@code member.events.v1} &mdash; lưu projection
     * cho từng thành viên với các trường allowlist: {@code displayName},
     * {@code givenName}, {@code surname}.
     *
     * @param records tập các bản ghi Kafka.
     */
    @KafkaListener(topics = "member.events.v1", groupId = "${spring.application.name}")
    public void onMember(ConsumerRecords<String, Object> records) {
        for (ConsumerRecord<String, Object> r : records) {
            try {
                if (!shouldProcess(r, "member.events.v1")) continue;
                long aggRev = parseLong(headerString(r, "revision"), 0L);
                long epoch = parseLong(headerString(r, "epoch"), 0L);
                UUID treeId = parseUuid(headerString(r, "treeId"));
                UUID memberId = parseUuid(headerString(r, "memberId"));
                String eventType = headerString(r, "event_type");

                // Xây dựng allowlist map với các khoá được phép công khai.
                Map<String, Object> allowlisted = new HashMap<>();
                allowlisted.put("memberId", memberId.toString());
                allowlisted.put("treeId", treeId.toString());
                if (payload(r) != null) {
                    for (String allowed : java.util.List.of("displayName", "givenName", "surname")) {
                        Object value = payload(r).get(allowed);
                        if (value != null) allowlisted.put(allowed, value);
                    }
                }
                boolean tombstoned = "MemberTombstoned".equals(eventType);
                projections.saveMember(treeId, memberId, allowlisted, tombstoned, Instant.now());
                recordReplay(treeId, "member.events.v1", r.partition(), r.offset(), aggRev, epoch, Instant.now());
                metrics.consumerProcessed("sharing-service", "member");
            } catch (Exception ex) {
                LOG.error("Failed to process member offset={}", r.offset(), ex);
            }
        }
    }

    /**
     * Xử lý các bản ghi từ topic {@code media.events.v1} &mdash; lưu projection
     * cho media với các trường allowlist: {@code kind}, {@code filename},
     * {@code mimeType}.
     *
     * @param records tập các bản ghi Kafka.
     */
    @KafkaListener(topics = "media.events.v1", groupId = "${spring.application.name}")
    public void onMedia(ConsumerRecords<String, Object> records) {
        for (ConsumerRecord<String, Object> r : records) {
            try {
                if (!shouldProcess(r, "media.events.v1")) continue;
                long aggRev = parseLong(headerString(r, "revision"), 0L);
                long epoch = parseLong(headerString(r, "epoch"), 0L);
                UUID treeId = parseUuid(headerString(r, "treeId"));
                UUID mediaId = parseUuid(headerString(r, "mediaId"));
                Map<String, Object> allowlisted = new HashMap<>();
                allowlisted.put("mediaId", mediaId.toString());
                allowlisted.put("treeId", treeId.toString());
                if (payload(r) != null) {
                    for (String allowed : java.util.List.of("kind", "filename", "mimeType")) {
                        Object value = payload(r).get(allowed);
                        if (value != null) allowlisted.put(allowed, value);
                    }
                }
                String eventType = headerString(r, "event_type");
                boolean tombstoned = "MediaDetached".equals(eventType);
                projections.saveMedia(treeId, mediaId, allowlisted, tombstoned, Instant.now());
                recordReplay(treeId, "media.events.v1", r.partition(), r.offset(), aggRev, epoch, Instant.now());
                metrics.consumerProcessed("sharing-service", "media");
            } catch (Exception ex) {
                LOG.error("Failed to process media offset={}", r.offset(), ex);
            }
        }
    }

    /**
     * Xử lý các bản ghi từ topic {@code event.events.v1} &mdash; lưu projection
     * cho sự kiện trong cây. Không có trường payload bổ sung được allowlist ở
     * thời điểm hiện tại (chỉ metadata cơ bản).
     *
     * @param records tập các bản ghi Kafka.
     */
    @KafkaListener(topics = "event.events.v1", groupId = "${spring.application.name}")
    public void onEvent(ConsumerRecords<String, Object> records) {
        for (ConsumerRecord<String, Object> r : records) {
            try {
                if (!shouldProcess(r, "event.events.v1")) continue;
                long aggRev = parseLong(headerString(r, "revision"), 0L);
                long epoch = parseLong(headerString(r, "epoch"), 0L);
                UUID treeId = parseUuid(headerString(r, "treeId"));
                UUID eventId = parseUuid(headerString(r, "eventId"));
                Map<String, Object> allowlisted = new HashMap<>();
                allowlisted.put("eventId", eventId.toString());
                allowlisted.put("treeId", treeId.toString());
                boolean tombstoned = "EventTombstoned".equals(headerString(r, "event_type"));
                projections.saveEvent(treeId, eventId, allowlisted, tombstoned, Instant.now());
                recordReplay(treeId, "event.events.v1", r.partition(), r.offset(), aggRev, epoch, Instant.now());
                metrics.consumerProcessed("sharing-service", "event");
            } catch (Exception ex) {
                LOG.error("Failed to process event offset={}", r.offset(), ex);
            }
        }
    }

    /**
     * Xử lý các bản ghi từ topic {@code relationship.events.v1} &mdash; lưu
     * projection cho các quan hệ trong cây.
     *
     * @param records tập các bản ghi Kafka.
     */
    @KafkaListener(topics = "relationship.events.v1", groupId = "${spring.application.name}")
    public void onRelationship(ConsumerRecords<String, Object> records) {
        for (ConsumerRecord<String, Object> r : records) {
            try {
                if (!shouldProcess(r, "relationship.events.v1")) continue;
                long aggRev = parseLong(headerString(r, "revision"), 0L);
                long epoch = parseLong(headerString(r, "epoch"), 0L);
                UUID treeId = parseUuid(headerString(r, "treeId"));
                UUID relId = parseUuid(headerString(r, "relationshipId"));
                Map<String, Object> allowlisted = new HashMap<>();
                allowlisted.put("relationshipId", relId.toString());
                allowlisted.put("treeId", treeId.toString());
                boolean tombstoned = "RelationshipTombstoned".equals(headerString(r, "event_type"));
                projections.saveRelationship(treeId, relId, allowlisted, tombstoned, Instant.now());
                recordReplay(treeId, "relationship.events.v1", r.partition(), r.offset(), aggRev, epoch, Instant.now());
                metrics.consumerProcessed("sharing-service", "relationship");
            } catch (Exception ex) {
                LOG.error("Failed to process relationship offset={}", r.offset(), ex);
            }
        }
    }

    /**
     * Trích xuất và giải mã phần {@code payload} (JSON) từ header của bản ghi.
     *
     * @param r bản ghi Kafka.
     * @return {@code Map<String,Object>} hoặc {@code null} nếu không có hoặc không hợp lệ.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> payload(ConsumerRecord<?, ?> r) {
        var h = r.headers().lastHeader("payload");
        if (h == null) return null;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(h.value(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            // Lỗi parse JSON không nên chặn cả batch &mdash; trả về null để caller xử lý.
            return null;
        }
    }

    /**
     * Kiểm tra bản ghi có nên xử lý hay không dựa trên cơ chế inbox.
     * <p>
     * Nếu thiếu {@code event_id} &mdash; bỏ qua và cảnh báo. Nếu đã có trong
     * inbox của {@code sharing-service} &mdash; bỏ qua (idempotent). Ngược lại,
     * ghi nhận vào inbox và cho phép xử lý.
     *
     * @param record bản ghi Kafka cần kiểm tra.
     * @param topic  tên topic &mdash; chỉ dùng cho log/metric.
     * @return {@code true} nếu bản ghi cần được xử lý.
     */
    private boolean shouldProcess(ConsumerRecord<?, ?> record, String topic) {
        String eventId = headerString(record, "event_id");
        if (eventId == null) {
            LOG.warn("Dropping record without event_id topic={} offset={}", topic, record.offset());
            return false;
        }
        // Idempotency check thông qua inbox.
        if (inbox.exists(eventId, "sharing-service")) {
            metrics.consumerDuplicate("sharing-service", topic);
            return false;
        }
        inbox.markProcessed(new com.familya.platform.inbox.InboxRecord(
                eventId, "sharing-service", topic, record.partition(), record.offset(), Instant.now()));
        return true;
    }

    /**
     * Cập nhật bảng {@code replay_ledger} với offset/revision/epoch đã xử lý.
     * Sử dụng {@code GREATEST} để tránh bị "lùi" khi nhận bản ghi cũ hơn.
     *
     * @param treeId    định danh cây gia phả.
     * @param topic     tên topic.
     * @param partition số partition.
     * @param offset    offset Kafka.
     * @param rev       phiên bản aggregate.
     * @param epoch     epoch.
     * @param now       thời điểm ghi nhận.
     */
    private void recordReplay(UUID treeId, String topic, int partition, long offset, long rev, long epoch, Instant now) {
        var entry = ReplayLedger.newEntry(treeId, topic, partition, offset, rev, epoch);
        jdbc.update(
                "INSERT INTO replay_ledger (aggregate_id, topic, partition_no, last_seen_offset, aggregate_revision, epoch, recorded_at) "
                        + "VALUES (:a, :t, :p, :o, :r, :e, :ts) "
                        + "ON DUPLICATE KEY UPDATE last_seen_offset = GREATEST(last_seen_offset, VALUES(last_seen_offset)), "
                        + "aggregate_revision = GREATEST(aggregate_revision, VALUES(aggregate_revision)), "
                        + "epoch = GREATEST(epoch, VALUES(epoch)), recorded_at = VALUES(recorded_at)",
                new MapSqlParameterSource()
                        .addValue("a", entry.aggregateId().toString())
                        .addValue("t", entry.topic())
                        .addValue("p", entry.partition())
                        .addValue("o", entry.lastSeenOffset())
                        .addValue("r", entry.aggregateRevision())
                        .addValue("e", entry.epoch())
                        .addValue("ts", Timestamp.from(entry.recordedAt())));
    }

    /**
     * Đọc một header của bản ghi Kafka dưới dạng chuỗi UTF-8.
     *
     * @param record bản ghi Kafka.
     * @param name   tên header cần đọc.
     * @return giá trị chuỗi hoặc {@code null} nếu không có header.
     */
    private static String headerString(ConsumerRecord<?, ?> record, String name) {
        var h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value());
    }

    /**
     * Phân tích một chuỗi thành {@code long}, trả về giá trị mặc định nếu lỗi.
     *
     * @param s        chuỗi đầu vào.
     * @param fallback giá trị trả về khi {@code null} hoặc không hợp lệ.
     * @return giá trị {@code long} đã phân tích hoặc {@code fallback}.
     */
    private static long parseLong(String s, long fallback) {
        try { return s == null ? fallback : Long.parseLong(s); } catch (NumberFormatException e) { return fallback; }
    }

    /**
     * Phân tích một chuỗi thành {@link UUID}, trả về {@code null} nếu lỗi.
     *
     * @param s chuỗi UUID.
     * @return {@link UUID} hoặc {@code null}.
     */
    private static UUID parseUuid(String s) {
        try { return s == null ? null : UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }
}