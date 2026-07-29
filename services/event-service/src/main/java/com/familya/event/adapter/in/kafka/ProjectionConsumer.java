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
 * Consumes the membership and member/media event streams and updates
 * the local projections. Inbox dedup is performed before any side
 * effect.
 */
@Component
public class ProjectionConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectionConsumer.class);

    private final InboxStore inbox;
    private final NamedParameterJdbcTemplate jdbc;
    private final PlatformMetrics metrics;

    public ProjectionConsumer(InboxStore inbox, NamedParameterJdbcTemplate jdbc, PlatformMetrics metrics) {
        this.inbox = inbox;
        this.jdbc = jdbc;
        this.metrics = metrics;
    }

    @KafkaListener(topics = "tree.memberships.v1", groupId = "${spring.application.name}")
    public void onMembership(ConsumerRecord<String, Object> r) {
        if (!shouldProcess(r, "tree.memberships.v1")) return;
        try {
            String eventType = headerString(r, "event_type");
            UUID treeId = UUID.fromString(headerString(r, "treeId"));
            UUID userId = UUID.fromString(headerString(r, "userId"));
            String role = headerString(r, "role");
            long rev = parseLong(headerString(r, "revision"), 0L);
            long epoch = parseLong(headerString(r, "epoch"), 0L);
            boolean revoked = "MembershipRevoked".equals(eventType);
            Instant now = Instant.now();
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
            metrics.consumerProcessed("event-service", "membership");
        } catch (Exception e) {
            LOG.error("Membership projection failure offset={}", r.offset(), e);
        }
    }

    @KafkaListener(topics = "member.events.v1", groupId = "${spring.application.name}")
    public void onMember(ConsumerRecord<String, Object> r) {
        if (!shouldProcess(r, "member.events.v1")) return;
        try {
            String eventType = headerString(r, "event_type");
            UUID treeId = UUID.fromString(headerString(r, "treeId"));
            UUID memberId = UUID.fromString(headerString(r, "memberId"));
            boolean exists = !"MemberTombstoned".equals(eventType);
            boolean tombstoned = "MemberTombstoned".equals(eventType);
            Instant now = Instant.now();
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

    private boolean shouldProcess(ConsumerRecord<String, Object> record, String topic) {
        String eventId = headerString(record, "event_id");
        if (eventId == null) {
            LOG.warn("Dropping record without event_id topic={} offset={}", topic, record.offset());
            return false;
        }
        if (inbox.exists(eventId, "event-service")) {
            metrics.consumerDuplicate("event-service", topic);
            return false;
        }
        inbox.markProcessed(new com.familya.platform.inbox.InboxRecord(
                eventId, "event-service", topic, record.partition(), record.offset(), Instant.now()));
        return true;
    }

    private static String headerString(ConsumerRecord<?, ?> record, String name) {
        var h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value());
    }

    private static long parseLong(String s, long fallback) {
        try { return s == null ? fallback : Long.parseLong(s); } catch (NumberFormatException e) { return fallback; }
    }
}