package com.familya.search.adapter.in.kafka;

import com.familya.platform.inbox.InboxStore;
import com.familya.platform.projection.ReplayLedger;
import com.familya.platform.telemetry.PlatformMetrics;
import com.familya.search.application.port.out.SearchWatermarkRepository;
import com.familya.search.domain.model.Watermark;
import com.familya.search.domain.normalizer.VietnameseNormalizer;
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

@Component
public class SearchProjectionConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(SearchProjectionConsumer.class);

    private final InboxStore inbox;
    private final NamedParameterJdbcTemplate jdbc;
    private final SearchWatermarkRepository watermark;
    private final PlatformMetrics metrics;

    public SearchProjectionConsumer(InboxStore inbox, NamedParameterJdbcTemplate jdbc,
                                     SearchWatermarkRepository watermark, PlatformMetrics metrics) {
        this.inbox = inbox;
        this.jdbc = jdbc;
        this.watermark = watermark;
        this.metrics = metrics;
    }

    @KafkaListener(topics = "tree.memberships.v1", groupId = "${spring.application.name}")
    public void onMembership(ConsumerRecords<String, Object> records) {
        for (ConsumerRecord<String, Object> r : records) {
            try {
                if (!shouldProcess(r, "tree.memberships.v1")) continue;
                long aggRev = parseLong(headerString(r, "revision"), 0L);
                long epoch = parseLong(headerString(r, "epoch"), 0L);
                UUID treeId = parseUuid(headerString(r, "treeId"));
                UUID userId = parseUuid(headerString(r, "userId"));
                String role = headerString(r, "role");
                String eventType = headerString(r, "event_type");
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
                watermark.advance(treeId, Watermark.Domain.TREE, aggRev);
                recordReplay(treeId, "tree.memberships.v1", r.partition(), r.offset(), aggRev, epoch, now);
                metrics.consumerProcessed("search-service", "membership");
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
                String displayName = headerString(r, "displayName");
                String givenName = headerString(r, "givenName");
                String surname = headerString(r, "surname");
                Integer birthYear = parseInt(headerString(r, "birthYear"));
                Integer deathYear = parseInt(headerString(r, "deathYear"));
                Integer generation = parseInt(headerString(r, "generation"));
                boolean tombstoned = "MemberTombstoned".equals(eventType);
                Instant now = Instant.now();
                String normalized = VietnameseNormalizer.normalize(displayName == null ? "" : displayName);
                jdbc.update(
                        "INSERT INTO search_member_doc (tree_id, member_id, full_name, given_name, surname, birth_year, death_year, tombstoned, normalized_name, last_updated) "
                                + "VALUES (:t, :m, :name, :g, :s, :by, :dy, :tomb, :norm, :u) "
                                + "ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), given_name = VALUES(given_name), surname = VALUES(surname), "
                                + "birth_year = VALUES(birth_year), death_year = VALUES(death_year), tombstoned = VALUES(tombstoned), "
                                + "normalized_name = VALUES(normalized_name), last_updated = VALUES(last_updated)",
                        new MapSqlParameterSource()
                                .addValue("t", treeId.toString())
                                .addValue("m", memberId.toString())
                                .addValue("name", displayName == null ? "" : displayName)
                                .addValue("g", givenName)
                                .addValue("s", surname)
                                .addValue("by", birthYear)
                                .addValue("dy", deathYear)
                                .addValue("tomb", tombstoned)
                                .addValue("norm", normalized)
                                .addValue("u", Timestamp.from(now)));
                if (generation != null) {
                    jdbc.update(
                            "INSERT INTO member_generation_projection (tree_id, member_id, generation, tombstoned, last_updated) "
                                    + "VALUES (:t, :m, :g, :tomb, :u) "
                                    + "ON DUPLICATE KEY UPDATE generation = VALUES(generation), tombstoned = VALUES(tombstoned), last_updated = VALUES(last_updated)",
                            new MapSqlParameterSource()
                                    .addValue("t", treeId.toString())
                                    .addValue("m", memberId.toString())
                                    .addValue("g", generation)
                                    .addValue("tomb", tombstoned)
                                    .addValue("u", Timestamp.from(now)));
                }
                upsertAutocomplete(treeId, memberId, displayName, givenName, surname, now);
                watermark.advance(treeId, Watermark.Domain.MEMBER, aggRev);
                recordReplay(treeId, "member.events.v1", r.partition(), r.offset(), aggRev, epoch, now);
                metrics.consumerProcessed("search-service", "member");
            } catch (Exception ex) {
                LOG.error("Failed to process member offset={}", r.offset(), ex);
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
                String title = headerString(r, "title");
                String startDate = headerString(r, "startDate");
                String kind = headerString(r, "kind");
                boolean tombstoned = "EventTombstoned".equals(headerString(r, "event_type"));
                String normalized = VietnameseNormalizer.normalize(title == null ? "" : title);
                Instant now = Instant.now();
                jdbc.update(
                        "INSERT INTO search_event_doc (tree_id, event_id, title, start_date, kind, tombstoned, normalized_title, last_updated) "
                                + "VALUES (:t, :e, :title, :d, :k, :tomb, :norm, :u) "
                                + "ON DUPLICATE KEY UPDATE title = VALUES(title), start_date = VALUES(start_date), kind = VALUES(kind), "
                                + "tombstoned = VALUES(tombstoned), normalized_title = VALUES(normalized_title), last_updated = VALUES(last_updated)",
                        new MapSqlParameterSource()
                                .addValue("t", treeId.toString())
                                .addValue("e", eventId.toString())
                                .addValue("title", title == null ? "" : title)
                                .addValue("d", startDate)
                                .addValue("k", kind)
                                .addValue("tomb", tombstoned)
                                .addValue("norm", normalized)
                                .addValue("u", Timestamp.from(now)));
                upsertAutocomplete(treeId, eventId, title, null, null, now);
                watermark.advance(treeId, Watermark.Domain.EVENT, aggRev);
                recordReplay(treeId, "event.events.v1", r.partition(), r.offset(), aggRev, epoch, now);
                metrics.consumerProcessed("search-service", "event");
            } catch (Exception ex) {
                LOG.error("Failed to process event offset={}", r.offset(), ex);
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
                String filename = headerString(r, "filename");
                String kind = headerString(r, "kind");
                boolean tombstoned = "MediaDetached".equals(headerString(r, "event_type"));
                String normalized = VietnameseNormalizer.normalize(filename == null ? "" : filename);
                Instant now = Instant.now();
                jdbc.update(
                        "INSERT INTO search_media_doc (tree_id, media_id, filename, kind, tombstoned, normalized_filename, last_updated) "
                                + "VALUES (:t, :m, :name, :k, :tomb, :norm, :u) "
                                + "ON DUPLICATE KEY UPDATE filename = VALUES(filename), kind = VALUES(kind), tombstoned = VALUES(tombstoned), "
                                + "normalized_filename = VALUES(normalized_filename), last_updated = VALUES(last_updated)",
                        new MapSqlParameterSource()
                                .addValue("t", treeId.toString())
                                .addValue("m", mediaId.toString())
                                .addValue("name", filename == null ? "" : filename)
                                .addValue("k", kind == null ? "OTHER" : kind)
                                .addValue("tomb", tombstoned)
                                .addValue("norm", normalized)
                                .addValue("u", Timestamp.from(now)));
                upsertAutocomplete(treeId, mediaId, filename, null, null, now);
                watermark.advance(treeId, Watermark.Domain.MEDIA, aggRev);
                recordReplay(treeId, "media.events.v1", r.partition(), r.offset(), aggRev, epoch, now);
                metrics.consumerProcessed("search-service", "media");
            } catch (Exception ex) {
                LOG.error("Failed to process media offset={}", r.offset(), ex);
            }
        }
    }

    private void upsertAutocomplete(UUID treeId, UUID ownerId, String surface, String given, String surname, Instant now) {
        if (surface == null || surface.isBlank()) return;
        String normalized = VietnameseNormalizer.normalize(surface);
        String prefix = normalized.length() >= 3 ? normalized.substring(0, 3) : normalized;
        jdbc.update(
                "INSERT INTO autocomplete_entry (tree_id, owner_id, surface, normalized_prefix, weight, last_updated) "
                        + "VALUES (:t, :o, :s, :p, :w, :u) "
                        + "ON DUPLICATE KEY UPDATE surface = VALUES(surface), weight = VALUES(weight), last_updated = VALUES(last_updated)",
                new MapSqlParameterSource()
                        .addValue("t", treeId.toString())
                        .addValue("o", ownerId == null ? null : ownerId.toString())
                        .addValue("s", surface)
                        .addValue("p", prefix)
                        .addValue("w", 100)
                        .addValue("u", Timestamp.from(now)));
    }

    private boolean shouldProcess(ConsumerRecord<?, ?> record, String topic) {
        String eventId = headerString(record, "event_id");
        if (eventId == null) {
            LOG.warn("Dropping record without event_id topic={} offset={}", topic, record.offset());
            return false;
        }
        if (inbox.exists(eventId, "search-service")) {
            metrics.consumerDuplicate("search-service", topic);
            return false;
        }
        inbox.markProcessed(new com.familya.platform.inbox.InboxRecord(
                eventId, "search-service", topic, record.partition(), record.offset(), Instant.now()));
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

    private static int parseInt(String s) {
        try { return s == null ? 0 : Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }

    private static UUID parseUuid(String s) {
        try { return s == null ? null : UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }
}
