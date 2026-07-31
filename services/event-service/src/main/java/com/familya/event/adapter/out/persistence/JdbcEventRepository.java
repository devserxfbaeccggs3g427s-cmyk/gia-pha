package com.familya.event.adapter.out.persistence;

import com.familya.event.application.port.out.EventRepository;
import com.familya.event.domain.model.DomainEvent;
import com.familya.event.domain.model.RecurrenceRule;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Triển khai {@link EventRepository} bằng JDBC — thao tác trực tiếp với
 * cơ sở dữ liệu MySQL thông qua {@link NamedParameterJdbcTemplate}.
 *
 * <h2>Bảng dữ liệu</h2>
 * <ul>
 *   <li>{@code domain_event}: bảng chính lưu trữ aggregate.</li>
 *   <li>{@code saga_compensation_snapshot}: lưu snapshot bù cho Saga.</li>
 * </ul>
 *
 * <h2>Serialize phức tạp</h2>
 * <p>Các trường phức tạp được serialize thành JSON:
 * <ul>
 *   <li>{@code recurrence_json}: lưu {@link RecurrenceRule} dạng JSON.</li>
 *   <li>{@code additional_member_ids}, {@code media_refs}: danh sách UUID
 *       dạng JSON.</li>
 * </ul>
 *
 * <h2>Quản lý transaction</h2>
 * <p>Tất cả phương thức ghi sử dụng {@link Propagation#MANDATORY} — yêu
 * cầu caller đã mở transaction; đảm bảo tính atomic giữa aggregate và
 * outbox (xem {@link com.familya.event.adapter.out.events.OutboxEventChangePublisher}).
 *
 * @author gia-pha platform
 */
@Component
public class JdbcEventRepository implements EventRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Khởi tạo adapter.
     *
     * @param jdbc JDBC template.
     */
    public JdbcEventRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Lưu ý: sử dụng {@code MANDATORY} propagation — chỉ chạy trong
     * transaction của caller; lý do: đảm bảo consistency với outbox.
     *
     * @throws org.springframework.dao.DuplicateKeyException nếu trùng khóa chính.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(DomainEvent ev) {
        jdbc.update(
                "INSERT INTO domain_event (id, tree_id, title, description, kind, start_date, end_date, "
                        + "recurrence_json, primary_member_id, additional_member_ids, media_refs, location, "
                        + "revision, created_at, updated_at, version) "
                        + "VALUES (:id, :tree, :title, :desc, :kind, :start, :end, "
                        + ":recur, :primary, :additional, :media, :loc, :rev, :created, :updated, :v)",
                params(ev));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Thực hiện trong transaction read-only để tối ưu hóa tài nguyên.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<DomainEvent> findById(UUID id) {
        var rows = jdbc.queryForList(
                "SELECT id, tree_id, title, description, kind, start_date, end_date, "
                        + "recurrence_json, primary_member_id, additional_member_ids, media_refs, location, "
                        + "revision, created_at, updated_at, tombstoned_at, version "
                        + "FROM domain_event WHERE id = :id",
                new MapSqlParameterSource("id", id.toString()));
        return rows.isEmpty() ? Optional.empty() : Optional.of(fromRow(rows.get(0)));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Có hai nhánh SQL — một có {@code tombstoned_at IS NULL}, một
     * không — dựa trên {@code includeTombstoned}.
     */
    @Override
    @Transactional(readOnly = true)
    public List<DomainEvent> listByTree(UUID treeId, boolean includeTombstoned) {
        String sql = includeTombstoned
                ? "SELECT * FROM domain_event WHERE tree_id = :t ORDER BY created_at"
                : "SELECT * FROM domain_event WHERE tree_id = :t AND tombstoned_at IS NULL ORDER BY created_at";
        var rows = jdbc.queryForList(sql, new MapSqlParameterSource("t", treeId.toString()));
        return rows.stream().map(this::fromRow).toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Sử dụng MySQL {@code JSON_CONTAINS} để tìm các sự kiện có
     * {@code memberId} trong {@code additional_member_ids} (mảng JSON)
     * <i>hoặc</i> trùng {@code primary_member_id}.
     */
    @Override
    @Transactional(readOnly = true)
    public List<DomainEvent> listReferencingMember(UUID treeId, UUID memberId) {
        var rows = jdbc.queryForList(
                "SELECT id, tree_id, title, description, kind, start_date, end_date, "
                        + "recurrence_json, primary_member_id, additional_member_ids, media_refs, location, "
                        + "revision, created_at, updated_at, tombstoned_at, version "
                        + "FROM domain_event WHERE tree_id = :t AND tombstoned_at IS NULL "
                        + "AND (primary_member_id = :m OR JSON_CONTAINS(additional_member_ids, JSON_QUOTE(:m)))",
                new MapSqlParameterSource()
                        .addValue("t", treeId.toString())
                        .addValue("m", memberId.toString()));
        return rows.stream().map(this::fromRow).toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Sử dụng {@code ON DUPLICATE KEY UPDATE} để đảm bảo idempotent —
     * viết lại snapshot nếu đã tồn tại cho cùng {@code operationId}.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveCompensationSnapshot(UUID operationId, String snapshotJson) {
        jdbc.update(
                "INSERT INTO saga_compensation_snapshot (operation_id, snapshot_json, recorded_at) "
                        + "VALUES (:id, :snap, :ts) "
                        + "ON DUPLICATE KEY UPDATE snapshot_json = VALUES(snapshot_json), recorded_at = VALUES(recorded_at)",
                new MapSqlParameterSource()
                        .addValue("id", operationId.toString())
                        .addValue("snap", snapshotJson)
                        .addValue("ts", Timestamp.from(java.time.Instant.now())));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public String loadCompensationSnapshot(UUID operationId) {
        var rows = jdbc.queryForList(
                "SELECT snapshot_json FROM saga_compensation_snapshot WHERE operation_id = :id",
                new MapSqlParameterSource("id", operationId.toString()));
        return rows.isEmpty() ? null : (String) rows.get(0).get("snapshot_json");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void update(DomainEvent ev) {
        jdbc.update(
                "UPDATE domain_event SET title = :title, description = :desc, kind = :kind, "
                        + "start_date = :start, end_date = :end, recurrence_json = :recur, "
                        + "primary_member_id = :primary, additional_member_ids = :additional, "
                        + "media_refs = :media, location = :loc, "
                        + "updated_at = :updated, tombstoned_at = :tomb, version = :v WHERE id = :id",
                params(ev));
    }

    /**
     * Tập hợp các tham số từ aggregate cho câu INSERT/UPDATE.
     *
     * <p>Lưu ý về serialize:
     * <ul>
     *   <li>{@code start}, {@code end}: chuyển LocalDate → {@link Date}
     *       (kiểu ngày của JDBC).</li>
     *   <li>{@code created}, {@code updated}, {@code tomb}: chuyển
     *       {@link java.time.Instant} → {@link Timestamp}.</li>
     *   <li>{@code recur}, {@code additional}, {@code media}: serialize
     *       thành JSON.</li>
     * </ul>
     *
     * @param ev aggregate nguồn.
     * @return {@link MapSqlParameterSource} đã gắn giá trị.
     */
    private MapSqlParameterSource params(DomainEvent ev) {
        return new MapSqlParameterSource()
                .addValue("id", ev.id().toString())
                .addValue("tree", ev.treeId().toString())
                .addValue("title", ev.title())
                .addValue("desc", ev.description())
                .addValue("kind", ev.kind().name())
                .addValue("start", ev.startDate() == null ? null : Date.valueOf(ev.startDate()))
                .addValue("end", ev.endDate() == null ? null : Date.valueOf(ev.endDate()))
                .addValue("recur", serializeRecurrence(ev.recurrence()))
                .addValue("primary", ev.primaryMemberId() == null ? null : ev.primaryMemberId().toString())
                .addValue("additional", serializeIds(ev.additionalMemberIds()))
                .addValue("media", serializeIds(ev.mediaRefs()))
                .addValue("loc", ev.location())
                .addValue("rev", ev.revision())
                .addValue("created", Timestamp.from(ev.createdAt()))
                .addValue("updated", Timestamp.from(ev.updatedAt()))
                .addValue("tomb", ev.tombstonedAt() == null ? null : Timestamp.from(ev.tombstonedAt()))
                .addValue("v", ev.version());
    }

    /**
     * Serialize {@link RecurrenceRule} thành JSON.
     *
     * <p>Định dạng: {@code {"frequency":"YEARLY","interval":1,"termination":{"count":5}}}.
     * Hai biến thể termination được biểu diễn bằng object khác nhau.
     *
     * @param r quy tắc lặp lại hoặc {@code null}.
     * @return chuỗi JSON hoặc {@code null}.
     */
    private String serializeRecurrence(RecurrenceRule r) {
        if (r == null) return null;
        try {
            return mapper.writeValueAsString(Map.of(
                    "frequency", r.frequency().name(),
                    "interval", r.interval(),
                    "termination", r.termination() instanceof RecurrenceRule.Count c
                            ? Map.of("count", c.count())
                            : Map.of("until", ((RecurrenceRule.Until) r.termination()).until().toString())));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    /**
     * Serialize danh sách UUID thành mảng JSON các chuỗi.
     *
     * @param ids danh sách UUID hoặc {@code null}.
     * @return chuỗi JSON hoặc {@code null}.
     */
    private String serializeIds(List<UUID> ids) {
        try { return ids == null ? null : mapper.writeValueAsString(ids.stream().map(UUID::toString).toList()); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    /**
     * Tái dựng aggregate từ một dòng kết quả JDBC.
     *
     * <p>Lưu ý về deserialize:
     * <ul>
     *   <li>JDBC trả về {@link Date} cho ngày — chuyển sang
     *       {@link LocalDate} qua {@code toLocalDate()}.</li>
     *   <li>Các cột JSON được parse qua {@link ObjectMapper}.</li>
     *   <li>Cột có thể {@code null} được kiểm tra trước khi ép kiểu.</li>
     * </ul>
     *
     * @param r dòng kết quả.
     * @return aggregate đã khôi phục.
     */
    private DomainEvent fromRow(java.util.Map<String, Object> r) {
        return new DomainEvent(
                UUID.fromString((String) r.get("id")),
                UUID.fromString((String) r.get("tree_id")),
                (String) r.get("title"),
                (String) r.get("description"),
                DomainEvent.Kind.valueOf((String) r.get("kind")),
                r.get("start_date") == null ? null : ((Date) r.get("start_date")).toLocalDate(),
                r.get("end_date") == null ? null : ((Date) r.get("end_date")).toLocalDate(),
                parseRecurrence((String) r.get("recurrence_json")),
                r.get("primary_member_id") == null ? null : UUID.fromString((String) r.get("primary_member_id")),
                parseIds((String) r.get("additional_member_ids")),
                parseIds((String) r.get("media_refs")),
                (String) r.get("location"),
                ((Number) r.get("revision")).longValue(),
                ((Timestamp) r.get("created_at")).toInstant(),
                ((Timestamp) r.get("updated_at")).toInstant(),
                r.get("tombstoned_at") == null ? null : ((Timestamp) r.get("tombstoned_at")).toInstant(),
                ((Number) r.get("version")).longValue());
    }

    /**
     * Parse chuỗi JSON thành {@link RecurrenceRule}. Ánh xạ termination
     * dựa trên sự tồn tại của khóa {@code count} hoặc {@code until}.
     *
     * @param json chuỗi JSON hoặc {@code null}.
     * @return {@link RecurrenceRule} hoặc {@code null}.
     */
    private RecurrenceRule parseRecurrence(String json) {
        if (json == null) return null;
        try {
            var m = mapper.readValue(json, new TypeReference<Map<String, Object>>() { });
            String freq = (String) m.get("frequency");
            int interval = ((Number) m.get("interval")).intValue();
            Map<String, Object> t = (Map<String, Object>) m.get("termination");
            RecurrenceRule.Termination term;
            if (t.containsKey("count")) term = new RecurrenceRule.Count(((Number) t.get("count")).intValue());
            else term = new RecurrenceRule.Until(LocalDate.parse((String) t.get("until")));
            return new RecurrenceRule(RecurrenceRule.Frequency.valueOf(freq), interval, term);
        } catch (Exception e) { throw new IllegalStateException("Cannot parse recurrence: " + json, e); }
    }

    /**
     * Parse chuỗi JSON thành {@link List} UUID. Trả về danh sách rỗng
     * nếu chuỗi là {@code null}.
     *
     * @param json chuỗi JSON hoặc {@code null}.
     * @return danh sách UUID.
     */
    private List<UUID> parseIds(String json) {
        if (json == null) return List.of();
        try {
            List<String> raw = mapper.readValue(json, new TypeReference<List<String>>() { });
            return raw.stream().map(UUID::fromString).toList();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
