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

@Component
public class SharingProjectionConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(SharingProjectionConsumer.class);

    private final InboxStore inbox;
    private final NamedParameterJdbcTemplate jdbc;
    private final AllowlistedProjectionRepository projections;
    private final PlatformMetrics metrics;

    public SharingProjectionConsumer(InboxStore inbox, NamedParameterJdbcTemplate jdbc,
                                     AllowlistedProjectionRepository projections, PlatformMetrics metrics) {
        this.inbox = inbox;
        this.jdbc = jdbc;
        this.projections = projections;
        this.metrics = metrics;
    }

    @KafkaListener(topics = "tree.memberships.v1", groupId = "${spring.application.name}")
    public void onMembership(ConsumerRecords<String, Object> records) {
        for (ConsumerRecord<String, Object> r : records) {
            try {
                if (!shouldProcess(r, "tree.memberships.v1")) continue;
                String eventType = headerString(r, "event_type");
                long aggRev = parseLong(headerString(r, "revision"), 0L);
                long epoch = parseLong(headerString(r, "epoch"), 0L);
                UUID treeId = parseUuid(headerString(r, "treeId"));
                UUID userId = parseUuid(headerString(r, "userId"));
                String role = headerString(r, "role");
                boolean revoked = "MembershipRevoked".equals(eventType);
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
                recordReplay(treeId, "tree.memberships.v1", r.partition(), r.offset(), aggRev, epoch, now);
                metrics.consumerProcessed("sharing-service", "membership");
            } catch (Exception ex) {
                LOG.error("Failed to process membership offset={}", r.offset(), ex);
            }
        }
    }

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

    @SuppressWarnings("unchecked")
    private Map<String, Object> payload(ConsumerRecord<?, ?> r) {
        var h = r.headers().lastHeader("payload");
        if (h == null) return null;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(h.value(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    private boolean shouldProcess(ConsumerRecord<?, ?> record, String topic) {
        String eventId = headerString(record, "event_id");
        if (eventId == null) {
            LOG.warn("Dropping record without event_id topic={} offset={}", topic, record.offset());
            return false;
        }
        if (inbox.exists(eventId, "sharing-service")) {
            metrics.consumerDuplicate("sharing-service", topic);
            return false;
        }
        inbox.markProcessed(new com.familya.platform.inbox.InboxRecord(
                eventId, "sharing-service", topic, record.partition(), record.offset(), Instant.now()));
        return true;
    }

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
