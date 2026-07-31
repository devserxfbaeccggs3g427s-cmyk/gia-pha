package com.familya.relationship.adapter.in.kafka;

import com.familya.platform.inbox.InboxStore;
import com.familya.platform.telemetry.PlatformMetrics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
 * Consumer Kafka chịu trách nhiệm cập nhật hai projection cục bộ:
 * <ul>
 *   <li><b>{@code authorization_projection}</b> - feed từ stream
 *       {@code tree.memberships.v1} của Tree service.</li>
 *   <li><b>{@code member_existence_projection}</b> - feed từ stream
 *       {@code member.events.v1} của Member service.</li>
 * </ul>
 *
 * <p>
 * Trước khi xử lý, mỗi bản ghi được kiểm tra qua {@link InboxStore} (idempotency
 * thông qua {@code event_id}). Nhờ vậy, khi consumer khởi động lại hoặc nhận
 * lại bản ghi cũ, hệ thống không xử lý trùng lặp.
 * </p>
 */
@Component
public class ProjectionConsumer {

    /** Logger ghi nhận hoạt động. */
    private static final Logger LOG = LoggerFactory.getLogger(ProjectionConsumer.class);

    /** Kho inbox để kiểm tra idempotency. */
    private final InboxStore inbox;
    /** Template JDBC để ghi projection. */
    private final NamedParameterJdbcTemplate jdbc;
    /** Bộ đếm metric. */
    private final PlatformMetrics metrics;

    /**
     * Khởi tạo consumer.
     *
     * @param inbox   kho inbox
     * @param jdbc    template JDBC
     * @param metrics bộ đếm metric
     */
    public ProjectionConsumer(InboxStore inbox, NamedParameterJdbcTemplate jdbc, PlatformMetrics metrics) {
        this.inbox = inbox;
        this.jdbc = jdbc;
        this.metrics = metrics;
    }

    /**
     * Consumer cho stream {@code tree.memberships.v1}.
     * <p>
     * Quy trình:
     * </p>
     * <ol>
     *   <li>Kiểm tra idempotency qua {@link #shouldProcess}.</li>
     *   <li>Trích xuất các trường từ header Kafka.</li>
     *   <li>Xác định {@code revoked} dựa trên {@code event_type}.</li>
     *   <li>Upsert vào {@code authorization_projection}.</li>
     *   <li>Ghi nhận metric.</li>
     * </ol>
     *
     * @param r bản ghi Kafka
     */
    @KafkaListener(topics = "tree.memberships.v1", groupId = "${spring.application.name}")
    public void onMembership(ConsumerRecord<String, Object> r) {
        // Bước 1: kiểm tra idempotency - bỏ qua nếu đã xử lý.
        if (!shouldProcess(r, "tree.memberships.v1")) return;
        try {
            // Bước 2: trích xuất các trường từ header Kafka (xem helper headerString).
            String eventType = headerString(r, "event_type");
            UUID treeId = UUID.fromString(headerString(r, "treeId"));
            UUID userId = UUID.fromString(headerString(r, "userId"));
            String role = headerString(r, "role");
            long rev = parseLong(headerString(r, "revision"), 0L);
            long epoch = parseLong(headerString(r, "epoch"), 0L);
            // Bước 3: xác định cờ thu hồi. MembershipRevoked tương ứng với revoked=true,
            // đồng thời role bị đặt về null.
            boolean revoked = "MembershipRevoked".equals(eventType);
            Instant now = Instant.now();
            // Bước 4: upsert vào authorization_projection.
            jdbc.update(
                    "INSERT INTO authorization_projection "
                            + "(tree_id, user_id, role, revision, epoch, granted_at, revoked, source_event_id, last_updated_at) "
                            + "VALUES (:t, :u, :role, :rev, :e, :at, :revoked, :src, :upd) "
                            + "ON DUPLICATE KEY UPDATE role = VALUES(role), revision = VALUES(revision), "
                            + "epoch = VALUES(epoch), revoked = VALUES(revoked), "
                            + "source_event_id = VALUES(source_event_id), last_updated_at = VALUES(last_updated_at)",
                    new MapSqlParameterSource()
                            .addValue("t", treeId.toString())
                            .addValue("u", userId.toString())
                            .addValue("role", revoked ? null : role)
                            .addValue("rev", rev)
                            .addValue("e", epoch)
                            .addValue("at", Timestamp.from(now))
                            .addValue("revoked", revoked)
                            .addValue("src", headerString(r, "event_id"))
                            .addValue("upd", Timestamp.from(now)));
            // Bước 5: ghi nhận metric.
            metrics.consumerProcessed("relationship-service", "membership");
        } catch (Exception e) {
            // Lỗi xử lý - log để debug. Không ném ngoại lệ để tránh retry vòng lặp
            // cho các bản ghi "xấu" - chúng sẽ được xử lý bởi DLQ handler.
            LOG.error("Membership projection failure offset={}", r.offset(), e);
        }
    }

    /**
     * Consumer cho stream {@code member.events.v1}.
     * <p>
     * Quy trình:
     * </p>
     * <ol>
     *   <li>Kiểm tra idempotency qua {@link #shouldProcess}.</li>
     *   <li>Trích xuất các trường từ header Kafka.</li>
     *   <li>Quyết định {@code exists}/{@code tombstoned} dựa trên
     *       {@code event_type}.</li>
     *   <li>Upsert vào {@code member_existence_projection}.</li>
     *   <li>Ghi nhận metric.</li>
     * </ol>
     *
     * @param r bản ghi Kafka
     */
    @KafkaListener(topics = "member.events.v1", groupId = "${spring.application.name}")
    public void onMember(ConsumerRecord<String, Object> r) {
        // Bước 1: kiểm tra idempotency.
        if (!shouldProcess(r, "member.events.v1")) return;
        try {
            // Bước 2: trích xuất các trường.
            String eventType = headerString(r, "event_type");
            UUID treeId = UUID.fromString(headerString(r, "treeId"));
            UUID memberId = UUID.fromString(headerString(r, "memberId"));
            // Bước 3: ánh xạ trạng thái. MemberTombstoned → exists=false, tombstoned=true.
            // Các sự kiện khác (MemberCreated, MemberUpdated, ...) → exists=true, tombstoned=false.
            boolean exists = !"MemberTombstoned".equals(eventType);
            boolean tombstoned = "MemberTombstoned".equals(eventType);
            Instant now = Instant.now();
            // Bước 4: upsert vào member_existence_projection.
            jdbc.update(
                    "INSERT INTO member_existence_projection (tree_id, member_id, exists, tombstoned, last_updated) "
                            + "VALUES (:t, :m, :exists, :tomb, :upd) "
                            + "ON DUPLICATE KEY UPDATE exists = VALUES(exists), tombstoned = VALUES(tombstoned), last_updated = VALUES(last_updated)",
                    new MapSqlParameterSource()
                            .addValue("t", treeId.toString())
                            .addValue("m", memberId.toString())
                            .addValue("exists", exists)
                            .addValue("tomb", tombstoned)
                            .addValue("upd", Timestamp.from(now)));
            metrics.consumerProcessed("relationship-service", "member");
        } catch (Exception e) {
            LOG.error("Member projection failure offset={}", r.offset(), e);
        }
    }

    /**
     * Kiểm tra bản ghi có nên xử lý hay không dựa trên {@code InboxStore}.
     * <p>
     * Quy tắc:
     * </p>
     * <ul>
     *   <li>Nếu thiếu {@code event_id} → bỏ qua và log cảnh báo.</li>
     *   <li>Nếu {@code event_id} đã có trong inbox → bỏ qua và ghi metric duplicate.</li>
     *   <li>Nếu chưa có → đánh dấu đã xử lý và trả về {@code true}.</li>
     * </ul>
     *
     * @param record bản ghi Kafka
     * @param topic  tên topic (dùng cho log)
     * @return {@code true} nếu nên xử lý, {@code false} nếu bỏ qua
     */
    private boolean shouldProcess(ConsumerRecord<String, Object> record, String topic) {
        String eventId = headerString(record, "event_id");
        if (eventId == null) {
            // Thiếu event_id - bỏ qua vì không thể đảm bảo idempotency.
            LOG.warn("Dropping record without event_id topic={} offset={}", topic, record.offset());
            return false;
        }
        // Kiểm tra inbox đã có event_id chưa.
        if (inbox.exists(eventId, "relationship-service")) {
            metrics.consumerDuplicate("relationship-service", topic);
            return false;
        }
        // Đánh dấu đã xử lý để consumer khác / lần khởi động lại biết.
        inbox.markProcessed(new com.familya.platform.inbox.InboxRecord(
                eventId, "relationship-service", topic, record.partition(), record.offset(), Instant.now()));
        return true;
    }

    /**
     * Trích xuất giá trị header Kafka dưới dạng chuỗi.
     *
     * @param record bản ghi Kafka
     * @param name   tên header
     * @return giá trị header hoặc {@code null} nếu không có
     */
    private static String headerString(ConsumerRecord<?, ?> record, String name) {
        var h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value());
    }

    /**
     * Parse giá trị header dạng số nguyên dài; trả về {@code fallback} nếu không parse được.
     *
     * @param s        chuỗi đầu vào
     * @param fallback giá trị mặc định nếu parse thất bại hoặc {@code s == null}
     * @return giá trị số nguyên dài
     */
    private static long parseLong(String s, long fallback) {
        try { return s == null ? fallback : Long.parseLong(s); } catch (NumberFormatException e) { return fallback; }
    }
}