package com.familya.member.adapter.in.kafka;

import com.familya.member.application.port.out.MemberRepository;
import com.familya.member.domain.model.MemberAuthRow;
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
 * Consumes the {@code tree.memberships.v1} topic and updates the
 * local authorization projection. Uses inbox dedup and the
 * {@link ReplayLedger} for gap detection. Each event advances the
 * projection row's revision/epoch.
 */
@Component
public class MembershipProjectionConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(MembershipProjectionConsumer.class);

    private final MemberRepository repo;
    private final InboxStore inbox;
    private final NamedParameterJdbcTemplate jdbc;
    private final PlatformMetrics metrics;

    /**
     * Khởi tạo consumer với các phụ thuộc.
     *
     * @param repo    kho thành viên dùng để cập nhật projection auth
     * @param inbox   kho inbox cho dedup
     * @param jdbc    template JDBC cho bảng replay ledger
     * @param metrics bộ thu thập metric
     */
    public MembershipProjectionConsumer(MemberRepository repo, InboxStore inbox,
                                        NamedParameterJdbcTemplate jdbc, PlatformMetrics metrics) {
        this.repo = repo;
        this.inbox = inbox;
        this.jdbc = jdbc;
        this.metrics = metrics;
    }

    /**
     * Xử lý lô bản tin thành viên từ topic {@code tree.memberships.v1}.
     * Mỗi bản tin cập nhật projection auth cục bộ (vai trò, thu hồi) và ghi vào
     * {@code replay_ledger} để phát hiện khoảng trống dữ liệu.
     *
     * @param records tập các bản tin Kafka trong một lần poll
     */
    @KafkaListener(topics = "tree.memberships.v1", groupId = "${spring.application.name}")
    public void onMembership(ConsumerRecords<String, Object> records) {
        for (ConsumerRecord<String, Object> r : records) {
            try {
                // Bỏ qua nếu bản tin đã được xử lý hoặc thiếu event_id
                if (!shouldProcess(r, "tree.memberships.v1")) continue;
                // Trích xuất các metadata từ header — dùng để cập nhật projection
                String eventId = headerString(r, "event_id");
                String eventType = headerString(r, "event_type");
                long aggRev = parseLong(headerString(r, "revision"), 0L);
                long epoch = parseLong(headerString(r, "epoch"), 0L);
                UUID treeId = parseUuid(headerString(r, "treeId"));
                UUID userId = parseUuid(headerString(r, "userId"));
                String role = headerString(r, "role");
                // Sự kiện MembershipRevoked tương ứng với việc thu hồi vai trò (role = null)
                boolean revoked = "MembershipRevoked".equals(eventType);
                Instant now = Instant.now();
                // Cập nhật bảng projection auth với thông tin mới nhất
                repo.updateAuthRole(treeId, userId, revoked ? null : role, revoked,
                        aggRev, epoch, now, eventId, now);
                // Ghi nhận offset/revision/epoch vào replay_ledger dùng để phát hiện gap
                // Dùng GREATEST để tránh các bản tin đến muộn ghi đè giá trị lớn hơn
                jdbc.update(
                        "INSERT INTO replay_ledger (aggregate_id, topic, partition_no, last_seen_offset, aggregate_revision, epoch, recorded_at) "
                                + "VALUES (:a, :t, :p, :o, :r, :e, :ts) "
                                + "ON DUPLICATE KEY UPDATE last_seen_offset = GREATEST(last_seen_offset, VALUES(last_seen_offset)), "
                                + "aggregate_revision = GREATEST(aggregate_revision, VALUES(aggregate_revision)), "
                                + "epoch = GREATEST(epoch, VALUES(epoch)), recorded_at = VALUES(recorded_at)",
                        new MapSqlParameterSource()
                                .addValue("a", treeId.toString())
                                .addValue("t", "tree.memberships.v1")
                                .addValue("p", r.partition())
                                .addValue("o", r.offset())
                                .addValue("r", aggRev)
                                .addValue("e", epoch)
                                .addValue("ts", Timestamp.from(now)));
                metrics.consumerProcessed("member-service", "membership");
            } catch (Exception ex) {
                // Lỗi từng bản tin không làm dừng cả lô; chỉ ghi log để điều tra
                LOG.error("Failed to process membership offset={}", r.offset(), ex);
            }
        }
    }

    /**
     * Listener phụ cho topic {@code tree.events.v1} — hiện chỉ dùng để cập nhật metric.
     *
     * @param record bản tin Kafka từ topic sự kiện cây
     */
    @KafkaListener(topics = "tree.events.v1", groupId = "${spring.application.name}")
    public void onTree(ConsumerRecord<String, Object> record) {
        if (!shouldProcess(record, "tree.events.v1")) return;
        metrics.consumerProcessed("member-service", "tree");
        LOG.info("Processed tree event offset={} key={}", record.offset(), record.key());
    }

    /**
     * Kiểm tra một bản tin có nên xử lý hay không: yêu cầu có event_id và chưa có trong inbox.
     *
     * @param record bản tin Kafka
     * @param topic  tên topic (dùng cho log)
     * @return {@code true} nếu bản tin đủ điều kiện xử lý
     */
    private boolean shouldProcess(ConsumerRecord<String, Object> record, String topic) {
        String eventId = headerString(record, "event_id");
        if (eventId == null) {
            LOG.warn("Dropping record without event_id topic={} offset={}", topic, record.offset());
            return false;
        }
        if (inbox.exists(eventId, "member-service")) {
            metrics.consumerDuplicate("member-service", topic);
            return false;
        }
        inbox.markProcessed(new com.familya.platform.inbox.InboxRecord(
                eventId, "member-service", topic, record.partition(), record.offset(), Instant.now()));
        return true;
    }

    /** Lấy giá trị header dạng chuỗi của bản tin Kafka; trả về {@code null} nếu không tồn tại. */
    private static String headerString(ConsumerRecord<?, ?> record, String name) {
        var h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value());
    }

    /**
     * Phân tích giá trị số nguyên từ chuỗi; trả về {@code fallback} nếu không hợp lệ.
     *
     * @param s        chuỗi đầu vào (có thể null)
     * @param fallback giá trị mặc định
     * @return số nguyên đã phân tích hoặc fallback
     */
    private static long parseLong(String s, long fallback) {
        try { return s == null ? fallback : Long.parseLong(s); } catch (NumberFormatException e) { return fallback; }
    }

    /**
     * Phân tích UUID từ chuỗi; trả về {@code null} nếu không hợp lệ.
     *
     * @param s chuỗi đầu vào (có thể null)
     * @return UUID tương ứng hoặc null
     */
    private static UUID parseUuid(String s) {
        try { return s == null ? null : UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }
}