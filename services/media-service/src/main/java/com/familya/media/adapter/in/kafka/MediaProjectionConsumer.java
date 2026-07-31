package com.familya.media.adapter.in.kafka;

import com.familya.platform.inbox.InboxStore;
import com.familya.platform.projection.ReplayLedger;
import com.familya.platform.telemetry.PlatformMetrics;
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
import java.util.UUID;

/**
 * Adapter Kafka đầu vào (inbound) — consumer cho các projection liên quan media.
 * <p>
 * Tiêu thụ 3 topic để giữ các bảng projection nội bộ đồng bộ với upstream:
 * <ul>
 *   <li>{@code tree.memberships.v1} — cập nhật {@code authorization_projection}
 *       (role/revoked/revision/epoch cho mỗi (tree, user)).</li>
 *   <li>{@code member.events.v1} — cập nhật {@code member_reference_projection}
 *       (trạng thái tồn tại/tombstone).</li>
 *   <li>{@code event.events.v1} — cập nhật {@code event_reference_projection}.</li>
 * </ul>
 * Tất cả consumer đều dựa trên {@link InboxStore} để chống trùng lặp và {@link ReplayLedger}
 * để ghi nhận offset/revision đã xử lý phục vụ replay. Lỗi xử lý từng bản tin được
 * log và bỏ qua để không chặn offset (chiến lược "log-and-skip"); container Kafka
 * vẫn retry theo cấu hình ở mức batch.
 */
@Component
public class MediaProjectionConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(MediaProjectionConsumer.class);

    private final InboxStore inbox;
    private final NamedParameterJdbcTemplate jdbc;
    private final PlatformMetrics metrics;

    /**
     * Khởi tạo consumer.
     *
     * @param inbox   lưu trữ inbox dùng để chống trùng lặp event theo {@code event_id}.
     * @param jdbc    JDBC template cho các truy vấn UPSERT projection.
     * @param metrics bộ thu thập chỉ số nền tảng.
     */
    public MediaProjectionConsumer(InboxStore inbox, NamedParameterJdbcTemplate jdbc, PlatformMetrics metrics) {
        this.inbox = inbox;
        this.jdbc = jdbc;
        this.metrics = metrics;
    }

    /**
     * Listener cho topic {@code tree.memberships.v1}.
     * <p>
     * Cập nhật bảng {@code authorization_projection} bằng INSERT ... ON DUPLICATE KEY UPDATE.
     * Với {@code MembershipRevoked}, set {@code revoked=TRUE} và role=null. Idempotent —
     * xử lý trùng event_id sẽ bị bỏ qua bởi {@link InboxStore}.
     *
     * @param records lô bản tin Kafka.
     */
    @KafkaListener(topics = "tree.memberships.v1", groupId = "${spring.application.name}")
    public void onMembership(ConsumerRecords<String, Object> records) {
        for (ConsumerRecord<String, Object> r : records) {
            try {
                // Lọc trùng lặp và thiếu event_id qua inbox.
                if (!shouldProcess(r, "tree.memberships.v1")) continue;
                String eventType = headerString(r, "event_type");
                long aggRev = parseLong(headerString(r, "revision"), 0L);
                long epoch = parseLong(headerString(r, "epoch"), 0L);
                UUID treeId = parseUuid(headerString(r, "treeId"));
                UUID userId = parseUuid(headerString(r, "userId"));
                String role = headerString(r, "role");
                boolean revoked = "MembershipRevoked".equals(eventType);
                Instant now = Instant.now();
                // UPSERT projection: nếu đã có row (tree,user) thì cập nhật role/revoked/revision/epoch.
                jdbc.update(
                        "INSERT INTO authorization_projection (tree_id, user_id, role, revision, epoch, granted_at, revoked, source_event_id, last_updated_at) "
                                + "VALUES (:t, :u, :role, :rev, :epoch, :at, :revoked, :src, :upd) "
                                + "ON DUPLICATE KEY UPDATE role = VALUES(role), revision = VALUES(revision), epoch = VALUES(epoch), "
                                + "revoked = VALUES(revoked), source_event_id = VALUES(source_event_id), last_updated_at = VALUES(last_updated_at)",
                        new MapSqlParameterSource()
                                .addValue("t", treeId.toString())
                                .addValue("u", userId.toString())
                                // Khi revoke, xóa role để ProjectionMediaAuthorization trả DENY dù projection vẫn tồn tại.
                                .addValue("role", revoked ? null : role)
                                .addValue("rev", aggRev)
                                .addValue("epoch", epoch)
                                .addValue("at", Timestamp.from(now))
                                .addValue("revoked", revoked)
                                .addValue("src", headerString(r, "event_id"))
                                .addValue("upd", Timestamp.from(now)));
                // Ghi nhận vào replay ledger để phục vụ rebuild projection.
                recordReplay(treeId, "tree.memberships.v1", r.partition(), r.offset(), aggRev, epoch, now);
                metrics.consumerProcessed("media-service", "membership");
            } catch (Exception ex) {
                // Log và bỏ qua để không chặn offset; container sẽ retry theo cấu hình batch.
                LOG.error("Failed to process membership offset={}", r.offset(), ex);
            }
        }
    }

    /**
     * Listener cho topic {@code member.events.v1}.
     * <p>
     * Duy trì bảng {@code member_reference_projection} (exists/tombstoned) để
     * {@code AssociateMediaUseCase} có thể kiểm tra nhanh khi gắn media vào member.
     *
     * @param records lô bản tin Kafka.
     */
    @KafkaListener(topics = "member.events.v1", groupId = "${spring.application.name}")
    public void onMember(ConsumerRecords<String, Object> records) {
        for (ConsumerRecord<String, Object> r : records) {
            try {
                if (!shouldProcess(r, "member.events.v1")) continue;
                String eventType = headerString(r, "event_type");
                long aggRev = parseLong(headerString(r, "revision"), 0L);
                long epoch = parseLong(headerString(r, "epoch"), 0L);
                UUID treeId = parseUuid(headerString(r, "treeId"));
                UUID memberId = parseUuid(headerString(r, "memberId"));
                // MemberTombstoned: exists=false, tombstoned=true; ngược lại coi như member sống.
                boolean exists = !"MemberTombstoned".equals(eventType);
                boolean tombstoned = "MemberTombstoned".equals(eventType);
                Instant now = Instant.now();
                jdbc.update(
                        "INSERT INTO member_reference_projection (tree_id, member_id, exists, tombstoned, last_updated_at) "
                                + "VALUES (:t, :m, :e, :tomb, :u) "
                                + "ON DUPLICATE KEY UPDATE exists = VALUES(exists), tombstoned = VALUES(tombstoned), last_updated_at = VALUES(last_updated_at)",
                        new MapSqlParameterSource()
                                .addValue("t", treeId.toString())
                                .addValue("m", memberId.toString())
                                .addValue("e", exists)
                                .addValue("tomb", tombstoned)
                                .addValue("u", Timestamp.from(now)));
                recordReplay(treeId, "member.events.v1", r.partition(), r.offset(), aggRev, epoch, now);
                metrics.consumerProcessed("media-service", "member");
            } catch (Exception ex) {
                LOG.error("Failed to process member offset={}", r.offset(), ex);
            }
        }
    }

    /**
     * Listener cho topic {@code event.events.v1}.
     * <p>
     * Duy trì bảng {@code event_reference_projection} để validate target khi
     * {@code AssociateMediaUseCase} gắn media vào event.
     *
     * @param records lô bản tin Kafka.
     */
    @KafkaListener(topics = "event.events.v1", groupId = "${spring.application.name}")
    public void onEvent(ConsumerRecords<String, Object> records) {
        for (ConsumerRecord<String, Object> r : records) {
            try {
                if (!shouldProcess(r, "event.events.v1")) continue;
                String eventType = headerString(r, "event_type");
                long aggRev = parseLong(headerString(r, "revision"), 0L);
                long epoch = parseLong(headerString(r, "epoch"), 0L);
                UUID treeId = parseUuid(headerString(r, "treeId"));
                UUID eventId = parseUuid(headerString(r, "eventId"));
                boolean exists = !"EventTombstoned".equals(eventType);
                boolean tombstoned = "EventTombstoned".equals(eventType);
                Instant now = Instant.now();
                jdbc.update(
                        "INSERT INTO event_reference_projection (tree_id, event_id, exists, tombstoned, last_updated_at) "
                                + "VALUES (:t, :e, :ex, :tomb, :u) "
                                + "ON DUPLICATE KEY UPDATE exists = VALUES(exists), tombstoned = VALUES(tombstoned), last_updated_at = VALUES(last_updated_at)",
                        new MapSqlParameterSource()
                                .addValue("t", treeId.toString())
                                .addValue("e", eventId.toString())
                                .addValue("ex", exists)
                                .addValue("tomb", tombstoned)
                                .addValue("u", Timestamp.from(now)));
                recordReplay(treeId, "event.events.v1", r.partition(), r.offset(), aggRev, epoch, now);
                metrics.consumerProcessed("media-service", "event");
            } catch (Exception ex) {
                LOG.error("Failed to process event offset={}", r.offset(), ex);
            }
        }
    }

    /**
     * Kiểm tra bản tin có nên xử lý hay không dựa trên {@code event_id}.
     * <p>
     * Nếu thiếu {@code event_id} thì bỏ qua (không thể đảm bảo idempotency).
     * Nếu đã có trong inbox thì đánh dấu trùng lặp và bỏ qua.
     *
     * @param record bản tin Kafka.
     * @param topic  tên topic (chỉ dùng cho log).
     * @return {@code true} nếu nên xử lý tiếp.
     */
    private boolean shouldProcess(ConsumerRecord<?, ?> record, String topic) {
        String eventId = headerString(record, "event_id");
        if (eventId == null) {
            LOG.warn("Dropping record without event_id topic={} offset={}", topic, record.offset());
            return false;
        }
        if (inbox.exists(eventId, "media-service")) {
            metrics.consumerDuplicate("media-service", topic);
            return false;
        }
        // Đánh dấu đã xử lý trước khi logic chính để tránh xử lý 2 lần nếu có lỗi downstream.
        inbox.markProcessed(new com.familya.platform.inbox.InboxRecord(
                eventId, "media-service", topic, record.partition(), record.offset(), Instant.now()));
        return true;
    }

    /**
     * Ghi nhận entry vào {@code replay_ledger}.
     * <p>
     * Dùng {@code GREATEST} để đảm bảo các giá trị offset/revision/epoch chỉ
     * tăng (chống lại out-of-order delivery).
     *
     * @param treeId    aggregate id (tree).
     * @param topic     tên topic Kafka.
     * @param partition partition.
     * @param offset    offset trong partition.
     * @param rev       aggregate revision.
     * @param epoch     aggregate epoch.
     * @param now       thời điểm ghi nhận.
     */
    private void recordReplay(UUID treeId, String topic, int partition, long offset, long rev, long epoch, Instant now) {
        var entry = ReplayLedger.newEntry(treeId, topic, partition, offset, rev, epoch);
        // GREATEST chống xử lý out-of-order khi cùng (tree) có 2 partition/offset khác nhau.
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

    /** Đọc header Kafka dưới dạng chuỗi (UTF-8), trả về null nếu header không tồn tại. */
    private static String headerString(ConsumerRecord<?, ?> record, String name) {
        var h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value());
    }

    /** Parse chuỗi thành long với giá trị dự phòng khi null/sai định dạng. */
    private static long parseLong(String s, long fallback) {
        try { return s == null ? fallback : Long.parseLong(s); } catch (NumberFormatException e) { return fallback; }
    }

    /** Parse chuỗi thành UUID với giá trị null khi null/sai định dạng. */
    private static UUID parseUuid(String s) {
        try { return s == null ? null : UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }
}
