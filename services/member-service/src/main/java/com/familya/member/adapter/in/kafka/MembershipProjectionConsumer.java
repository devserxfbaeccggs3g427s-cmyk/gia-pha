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

    public MembershipProjectionConsumer(MemberRepository repo, InboxStore inbox,
                                        NamedParameterJdbcTemplate jdbc, PlatformMetrics metrics) {
        this.repo = repo;
        this.inbox = inbox;
        this.jdbc = jdbc;
        this.metrics = metrics;
    }

    @KafkaListener(topics = "tree.memberships.v1", groupId = "${spring.application.name}")
    public void onMembership(ConsumerRecords<String, Object> records) {
        for (ConsumerRecord<String, Object> r : records) {
            try {
                if (!shouldProcess(r, "tree.memberships.v1")) continue;
                String eventId = headerString(r, "event_id");
                String eventType = headerString(r, "event_type");
                long aggRev = parseLong(headerString(r, "revision"), 0L);
                long epoch = parseLong(headerString(r, "epoch"), 0L);
                UUID treeId = parseUuid(headerString(r, "treeId"));
                UUID userId = parseUuid(headerString(r, "userId"));
                String role = headerString(r, "role");
                boolean revoked = "MembershipRevoked".equals(eventType);
                Instant now = Instant.now();
                repo.updateAuthRole(treeId, userId, revoked ? null : role, revoked,
                        aggRev, epoch, now, eventId, now);
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
                LOG.error("Failed to process membership offset={}", r.offset(), ex);
            }
        }
    }

    @KafkaListener(topics = "tree.events.v1", groupId = "${spring.application.name}")
    public void onTree(ConsumerRecord<String, Object> record) {
        if (!shouldProcess(record, "tree.events.v1")) return;
        metrics.consumerProcessed("member-service", "tree");
        LOG.info("Processed tree event offset={} key={}", record.offset(), record.key());
    }

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

    private static String headerString(ConsumerRecord<?, ?> record, String name) {
        var h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value());
    }

    private static long parseLong(String s, long fallback) {
        try { return s == null ? fallback : Long.parseLong(s); } catch (NumberFormatException e) { return fallback; }
    }

    private static UUID parseUuid(String s) {
        try { return s == null ? null : UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }
}