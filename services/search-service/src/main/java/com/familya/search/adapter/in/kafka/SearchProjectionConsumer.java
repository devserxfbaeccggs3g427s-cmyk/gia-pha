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

/**
 * Kafka consumer duy trì các bảng chiếu (projection) của search service.
 *
 * <p>Lắng nghe bốn topic:</p>
 * <ul>
 *   <li>{@code tree.memberships.v1} - cập nhật {@code authorization_projection}.</li>
 *   <li>{@code member.events.v1} - cập nhật {@code search_member_doc},
 *       {@code member_generation_projection} và {@code autocomplete_entry}.</li>
 *   <li>{@code event.events.v1} - cập nhật {@code search_event_doc} và
 *       {@code autocomplete_entry}.</li>
 *   <li>{@code media.events.v1} - cập nhật {@code search_media_doc} và
 *       {@code autocomplete_entry}.</li>
 * </ul>
 *
 * <p>Mỗi bản ghi đều được:</p>
 * <ol>
 *   <li>Kiểm tra idempotent thông qua {@link InboxStore}.</li>
 *   <li>Ghi vào các bảng tương ứng với {@code INSERT ... ON DUPLICATE KEY UPDATE}
 *       - cho phép replay không phá dữ liệu.</li>
 *   <li>Tính tiền tố đã chuẩn hoá và upsert vào bảng autocomplete.</li>
 *   <li>Nâng watermark theo miền tương ứng.</li>
 *   <li>Ghi nhận {@code replay_ledger} để phục vụ tái dựng.</li>
 * </ol>
 *
 * <p>Mọi lỗi xử lý một bản ghi đều được ghi log và bỏ qua (không làm dừng
 * consumer), đảm bảo một sự kiện hỏng không ảnh hưởng cả batch.</p>
 */
@Component
public class SearchProjectionConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(SearchProjectionConsumer.class);

    private final InboxStore inbox;
    private final NamedParameterJdbcTemplate jdbc;
    private final SearchWatermarkRepository watermark;
    private final PlatformMetrics metrics;

    /**
     * Khởi tạo consumer với các phụ thuộc bắt buộc.
     *
     * @param inbox    inbox dùng để chống xử lý trùng sự kiện.
     * @param jdbc     JDBC template dùng để ghi các bảng chiếu.
     * @param watermark cổng nâng watermark theo miền.
     * @param metrics  cổng ghi nhận telemetry.
     */
    public SearchProjectionConsumer(InboxStore inbox, NamedParameterJdbcTemplate jdbc,
                                     SearchWatermarkRepository watermark, PlatformMetrics metrics) {
        this.inbox = inbox;
        this.jdbc = jdbc;
        this.watermark = watermark;
        this.metrics = metrics;
    }

    /**
     * Xử lý một batch bản ghi từ topic {@code tree.memberships.v1}. Mỗi bản
     * ghi cập nhật (hoặc chèn) một hàng trong {@code authorization_projection}.
     *
     * @param records batch bản ghi do Kafka giao.
     */
    @KafkaListener(topics = "tree.memberships.v1", groupId = "${spring.application.name}")
    public void onMembership(ConsumerRecords<String, Object> records) {
        // Duyệt tuần tự từng bản ghi trong batch - mỗi lỗi xử lý chỉ ảnh
        // hưởng bản ghi đó (catch bên trong), các bản ghi khác vẫn được xử lý.
        for (ConsumerRecord<String, Object> r : records) {
            try {
                // Bước 1: Kiểm tra idempotent và ghi nhận đã xử lý.
                if (!shouldProcess(r, "tree.memberships.v1")) continue;
                // Bước 2: Đọc các header bắt buộc - phải có event_id (đã
                // kiểm ở shouldProcess), các trường khác có thể null và sẽ
                // được phân tích an toàn ở parse*.
                long aggRev = parseLong(headerString(r, "revision"), 0L);
                long epoch = parseLong(headerString(r, "epoch"), 0L);
                UUID treeId = parseUuid(headerString(r, "treeId"));
                UUID userId = parseUuid(headerString(r, "userId"));
                String role = headerString(r, "role");
                String eventType = headerString(r, "event_type");
                // Sự kiện MembershipRevoked đánh dấu thu hồi - ta ghi role=null.
                boolean revoked = "MembershipRevoked".equals(eventType);
                Instant now = Instant.now();
                // Bước 3: Upsert vào authorization_projection. Câu lệnh
                // INSERT ... ON DUPLICATE KEY UPDATE giúp replay an toàn.
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
                // Bước 4: Nâng watermark cho miền TREE.
                watermark.advance(treeId, Watermark.Domain.TREE, aggRev);
                // Bước 5: Ghi nhận replay ledger.
                recordReplay(treeId, "tree.memberships.v1", r.partition(), r.offset(), aggRev, epoch, now);
                metrics.consumerProcessed("search-service", "membership");
            } catch (Exception ex) {
                // Một bản ghi lỗi không được phát sinh ngoại lệ ra ngoài
                // (vì sẽ khiến Kafka dừng poll batch), chỉ log và bỏ qua.
                LOG.error("Failed to process membership offset={}", r.offset(), ex);
            }
        }
    }

    /**
     * Xử lý một batch từ topic {@code member.events.v1}: cập nhật
     * {@code search_member_doc}, {@code member_generation_projection} và
     * bảng autocomplete.
     *
     * @param records batch bản ghi do Kafka giao.
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
                String displayName = headerString(r, "displayName");
                String givenName = headerString(r, "givenName");
                String surname = headerString(r, "surname");
                Integer birthYear = parseInt(headerString(r, "birthYear"));
                Integer deathYear = parseInt(headerString(r, "deathYear"));
                Integer generation = parseInt(headerString(r, "generation"));
                boolean tombstoned = "MemberTombstoned".equals(eventType);
                Instant now = Instant.now();
                // Chuẩn hoá tên hiển thị để phục vụ tìm kiếm không phân biệt dấu.
                String normalized = VietnameseNormalizer.normalize(displayName == null ? "" : displayName);
                // Upsert vào search_member_doc - dùng ON DUPLICATE để replay an toàn.
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
                // Bảng thế hệ chỉ được cập nhật khi sự kiện mang theo
                // thông tin generation - tránh ghi đè giá trị null.
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
                // Cập nhật gợi ý - dùng displayName (không phải given+surname)
                // vì người dùng thường tìm theo tên hiển thị đầy đủ.
                upsertAutocomplete(treeId, memberId, displayName, givenName, surname, now);
                watermark.advance(treeId, Watermark.Domain.MEMBER, aggRev);
                recordReplay(treeId, "member.events.v1", r.partition(), r.offset(), aggRev, epoch, now);
                metrics.consumerProcessed("search-service", "member");
            } catch (Exception ex) {
                LOG.error("Failed to process member offset={}", r.offset(), ex);
            }
        }
    }

    /**
     * Xử lý batch từ topic {@code event.events.v1}: cập nhật
     * {@code search_event_doc} và bảng autocomplete.
     *
     * @param records batch bản ghi do Kafka giao.
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

    /**
     * Xử lý batch từ topic {@code media.events.v1}: cập nhật
     * {@code search_media_doc} và bảng autocomplete.
     *
     * @param records batch bản ghi do Kafka giao.
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

    /**
     * Upsert một mục autocomplete: chuẩn hoá {@code surface}, lấy 3 ký tự
     * đầu làm tiền tố, ghi vào bảng với trọng số cố định {@code 100}.
     *
     * @param treeId  định danh cây gia phả.
     * @param ownerId định danh thực thể sở hữu.
     * @param surface chuỗi hiển thị (tên, tiêu đề, tên tệp...).
     * @param given   tên đệm - chỉ dùng để tham chiếu, hiện không ảnh hưởng.
     * @param surname họ - chỉ dùng để tham chiếu, hiện không ảnh hưởng.
     * @param now     mốc thời gian hiện tại.
     */
    private void upsertAutocomplete(UUID treeId, UUID ownerId, String surface, String given, String surname, Instant now) {
        // Bỏ qua nếu surface rỗng - tránh tạo các mục gợi ý vô nghĩa.
        if (surface == null || surface.isBlank()) return;
        String normalized = VietnameseNormalizer.normalize(surface);
        // Tiền tố phục vụ LIKE 'prefix%' - dùng 3 ký tự đầu (nếu đủ) để
        // phân tán khoá cân bằng; nếu chuỗi quá ngắn thì dùng toàn bộ.
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

    /**
     * Kiểm tra một bản ghi đã được xử lý chưa (qua inbox) và nếu chưa thì
     * đánh dấu là đã xử lý. Đảm bảo tính idempotent khi replay.
     *
     * @param record bản ghi Kafka cần kiểm tra.
     * @param topic  tên topic (dùng để log/metric).
     * @return {@code true} nếu nên xử lý tiếp, {@code false} nếu bỏ qua.
     */
    private boolean shouldProcess(ConsumerRecord<?, ?> record, String topic) {
        // Mọi bản ghi phải có header event_id - nếu thiếu thì bỏ qua.
        String eventId = headerString(record, "event_id");
        if (eventId == null) {
            LOG.warn("Dropping record without event_id topic={} offset={}", topic, record.offset());
            return false;
        }
        // Inbox kiểm tra đã thấy eventId này chưa - nếu rồi thì bỏ qua.
        if (inbox.exists(eventId, "search-service")) {
            metrics.consumerDuplicate("search-service", topic);
            return false;
        }
        // Ghi nhận là đã xử lý trước khi tiếp tục - đảm bảo dù có lỗi
        // xảy ra ở bước sau thì lần replay tới cũng bỏ qua.
        inbox.markProcessed(new com.familya.platform.inbox.InboxRecord(
                eventId, "search-service", topic, record.partition(), record.offset(), Instant.now()));
        return true;
    }

    /**
     * Ghi nhận {@code replay_ledger} - bảng phục vụ tái dựng lại projection
     * từ sự kiện nguồn chân lý khi cần rebuild.
     *
     * @param treeId    định danh cây.
     * @param topic     tên topic nguồn.
     * @param partition phân vùng Kafka.
     * @param offset    offset cuối đã xử lý.
     * @param rev       phiên bản aggregate đã xử lý.
     * @param epoch     epoch tương ứng.
     * @param now       mốc thời gian ghi nhận.
     */
    private void recordReplay(UUID treeId, String topic, int partition, long offset, long rev, long epoch, Instant now) {
        var entry = ReplayLedger.newEntry(treeId, topic, partition, offset, rev, epoch);
        // GREATEST(...) đảm bảo replay không "tua lùi" - nếu bản ghi mới
        // có giá trị nhỏ hơn thì giữ giá trị cũ.
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
     * Đọc giá trị header dạng chuỗi của bản ghi Kafka.
     *
     * @param record bản ghi Kafka.
     * @param name   tên header.
     * @return giá trị chuỗi, hoặc {@code null} nếu header không tồn tại.
     */
    private static String headerString(ConsumerRecord<?, ?> record, String name) {
        var h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value());
    }

    /**
     * Phân tích chuỗi thành {@code long}, an toàn khi đầu vào null/không hợp lệ.
     *
     * @param s        chuỗi cần phân tích.
     * @param fallback giá trị trả về nếu phân tích lỗi hoặc đầu vào null.
     * @return số {@code long} hoặc {@code fallback}.
     */
    private static long parseLong(String s, long fallback) {
        try { return s == null ? fallback : Long.parseLong(s); } catch (NumberFormatException e) { return fallback; }
    }

    /**
     * Phân tích chuỗi thành {@code int}, an toàn khi đầu vào null/không hợp lệ.
     *
     * @param s chuỗi cần phân tích.
     * @return số {@code int}, hoặc {@code 0} nếu lỗi/null.
     */
    private static int parseInt(String s) {
        try { return s == null ? 0 : Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }

    /**
     * Phân tích chuỗi thành {@link UUID}, an toàn khi đầu vào null/không hợp lệ.
     *
     * @param s chuỗi cần phân tích.
     * @return {@link UUID} tương ứng, hoặc {@code null} nếu lỗi/null.
     */
    private static UUID parseUuid(String s) {
        try { return s == null ? null : UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }
}
