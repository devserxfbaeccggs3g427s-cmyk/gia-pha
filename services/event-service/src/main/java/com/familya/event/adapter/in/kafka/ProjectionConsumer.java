package com.familya.event.adapter.in.kafka;

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
 * Kafka consumer phụ trách <b>tiêu thụ các event stream</b> từ các
 * dịch vụ khác để cập nhật <i>projection cục bộ</i>.
 *
 * <h2>Ba topic được lắng nghe</h2>
 * <ul>
 *   <li>{@code tree.memberships.v1} (group {@code ${spring.application.name}}) —
 *       phân quyền từ treeaccess-service, ghi vào
 *       {@code authorization_projection}.</li>
 *   <li>{@code member.events.v1} — sự kiện thành viên từ member-service,
 *       ghi vào {@code member_reference_projection}.</li>
 *   <li>{@code media.events.v1} — sự kiện media từ media-service, ghi
 *       vào {@code media_reference_projection}.</li>
 * </ul>
 *
 * <h2>Cơ chế chống trùng lặp (idempotent)</h2>
 * <p>Mỗi record được kiểm tra qua {@link InboxStore} ở hàm
 * {@link #shouldProcess}. Nếu đã xử lý → bỏ qua và ghi metric
 * {@code consumer.duplicate}. Nếu chưa → đánh dấu đã xử lý rồi mới
 * thực hiện UPSERT projection.
 *
 * <p>Các thao tác được bọc trong khối try/catch để ghi log lỗi mà
 * không ném — hành vi này phù hợp cho projection vì việc retry có
 * thể do Kafka offset tự động commit hoặc do consumer container.
 *
 * @author gia-pha platform
 */
@Component
public class ProjectionConsumer {

    /** Logger dùng cho audit. */
    private static final Logger LOG = LoggerFactory.getLogger(ProjectionConsumer.class);

    private final InboxStore inbox;
    private final NamedParameterJdbcTemplate jdbc;
    private final PlatformMetrics metrics;

    /**
     * Khởi tạo consumer.
     *
     * @param inbox   kho lưu trữ trạng thái đã xử lý (idempotency).
     * @param jdbc    JDBC template để ghi projection.
     * @param metrics metric quan sát.
     */
    public ProjectionConsumer(InboxStore inbox, NamedParameterJdbcTemplate jdbc, PlatformMetrics metrics) {
        this.inbox = inbox;
        this.jdbc = jdbc;
        this.metrics = metrics;
    }

    /**
     * Xử lý một bản ghi phân quyền từ {@code tree.memberships.v1}.
     *
     * @param r record từ Kafka.
     */
    @KafkaListener(topics = "tree.memberships.v1", groupId = "${spring.application.name}")
    public void onMembership(ConsumerRecord<String, Object> r) {
        if (!shouldProcess(r, "tree.memberships.v1")) return;
        try {
            // Bước 1: trích các header bắt buộc.
            String eventType = headerString(r, "event_type");
            UUID treeId = UUID.fromString(headerString(r, "treeId"));
            UUID userId = UUID.fromString(headerString(r, "userId"));
            String role = headerString(r, "role");
            long rev = parseLong(headerString(r, "revision"), 0L);
            long epoch = parseLong(headerString(r, "epoch"), 0L);
            boolean revoked = "MembershipRevoked".equals(eventType);
            Instant now = Instant.now();

            // Bước 2: UPSERT projection phân quyền.
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

            // Bước 3: ghi metric.
            metrics.consumerProcessed("event-service", "membership");
        } catch (Exception e) {
            // Lỗi projection: ghi log để vận hành; Kafka offset vẫn commit
            // để tránh lặp vô hạn nếu lỗi là vĩnh viễn (operator xử lý thủ công).
            LOG.error("Membership projection failure offset={}", r.offset(), e);
        }
    }

    /**
     * Xử lý một bản ghi thành viên từ {@code member.events.v1}.
     *
     * @param r record từ Kafka.
     */
    @KafkaListener(topics = "member.events.v1", groupId = "${spring.application.name}")
    public void onMember(ConsumerRecord<String, Object> r) {
        if (!shouldProcess(r, "member.events.v1")) return;
        try {
            String eventType = headerString(r, "event_type");
            UUID treeId = UUID.fromString(headerString(r, "treeId"));
            UUID memberId = UUID.fromString(headerString(r, "memberId"));

            // Quy ước: sự kiện MemberTombstoned đánh dấu tombstoned=true, các sự kiện khác đánh exists=true.
            boolean exists = !"MemberTombstoned".equals(eventType);
            boolean tombstoned = "MemberTombstoned".equals(eventType);
            Instant now = Instant.now();

            // UPSERT projection thành viên.
            jdbc.update(
                    "INSERT INTO member_reference_projection (tree_id, member_id, exists, tombstoned, last_updated) "
                            + "VALUES (:t, :m, :exists, :tomb, :upd) "
                            + "ON DUPLICATE KEY UPDATE exists = VALUES(exists), tombstoned = VALUES(tombstoned), last_updated = VALUES(last_updated)",
                    new MapSqlParameterSource()
                            .addValue("t", treeId.toString())
                            .addValue("m", memberId.toString())
                            .addValue("exists", exists)
                            .addValue("tomb", tombstoned)
                            .addValue("upd", Timestamp.from(now)));

            metrics.consumerProcessed("event-service", "member");
        } catch (Exception e) {
            LOG.error("Member projection failure offset={}", r.offset(), e);
        }
    }

    /**
     * Xử lý một bản ghi media từ {@code media.events.v1}.
     *
     * @param r record từ Kafka.
     */
    @KafkaListener(topics = "media.events.v1", groupId = "${spring.application.name}")
    public void onMedia(ConsumerRecord<String, Object> r) {
        if (!shouldProcess(r, "media.events.v1")) return;
        try {
            String eventType = headerString(r, "event_type");
            UUID treeId = UUID.fromString(headerString(r, "treeId"));
            UUID mediaId = UUID.fromString(headerString(r, "mediaId"));
            boolean exists = !"MediaTombstoned".equals(eventType);
            boolean tombstoned = "MediaTombstoned".equals(eventType);
            Instant now = Instant.now();

            // UPSERT projection media.
            jdbc.update(
                    "INSERT INTO media_reference_projection (tree_id, media_id, exists, tombstoned, last_updated) "
                            + "VALUES (:t, :m, :exists, :tomb, :upd) "
                            + "ON DUPLICATE KEY UPDATE exists = VALUES(exists), tombstoned = VALUES(tombstoned), last_updated = VALUES(last_updated)",
                    new MapSqlParameterSource()
                            .addValue("t", treeId.toString())
                            .addValue("m", mediaId.toString())
                            .addValue("exists", exists)
                            .addValue("tomb", tombstoned)
                            .addValue("upd", Timestamp.from(now)));

            metrics.consumerProcessed("event-service", "media");
        } catch (Exception e) {
            LOG.error("Media projection failure offset={}", r.offset(), e);
        }
    }

    /**
     * Kiểm tra một record có nên xử lý hay không. Đồng thời ghi nhận
     * "đã xử lý" vào {@link InboxStore} để chống trùng lặp.
     *
     * @param record record Kafka.
     * @param topic  tên topic — dùng cho log/metric.
     * @return {@code true} nếu nên xử lý; {@code false} nếu bỏ qua.
     */
    private boolean shouldProcess(ConsumerRecord<String, Object> record, String topic) {
        // Bước 1: yêu cầu bắt buộc — phải có event_id trong header.
        String eventId = headerString(record, "event_id");
        if (eventId == null) {
            LOG.warn("Dropping record without event_id topic={} offset={}", topic, record.offset());
            return false;
        }

        // Bước 2: chống trùng — nếu đã xử lý thì bỏ qua và ghi metric.
        if (inbox.exists(eventId, "event-service")) {
            metrics.consumerDuplicate("event-service", topic);
            return false;
        }

        // Bước 3: đánh dấu đã xử lý rồi mới cho phép xử lý tiếp.
        inbox.markProcessed(new com.familya.platform.inbox.InboxRecord(
                eventId, "event-service", topic, record.partition(), record.offset(), Instant.now()));
        return true;
    }

    /**
     * Đọc một header Kafka ra chuỗi UTF-8.
     *
     * @param record record Kafka.
     * @param name   tên header.
     * @return giá trị chuỗi hoặc {@code null} nếu header không tồn tại.
     */
    private static String headerString(ConsumerRecord<?, ?> record, String name) {
        var h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value());
    }

    /**
     * Parse một chuỗi số dài an toàn — trả về {@code fallback} nếu lỗi.
     *
     * @param s        chuỗi đầu vào.
     * @param fallback giá trị trả về nếu parse thất bại.
     * @return số dài đã parse.
     */
    private static long parseLong(String s, long fallback) {
        try { return s == null ? fallback : Long.parseLong(s); }
        catch (NumberFormatException e) { return fallback; }
    }
}
