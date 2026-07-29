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

@Component
public class JdbcEventRepository implements EventRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public JdbcEventRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

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

    @Override
    @Transactional(readOnly = true)
    public List<DomainEvent> listByTree(UUID treeId, boolean includeTombstoned) {
        String sql = includeTombstoned
                ? "SELECT * FROM domain_event WHERE tree_id = :t ORDER BY created_at"
                : "SELECT * FROM domain_event WHERE tree_id = :t AND tombstoned_at IS NULL ORDER BY created_at";
        var rows = jdbc.queryForList(sql, new MapSqlParameterSource("t", treeId.toString()));
        return rows.stream().map(this::fromRow).toList();
    }

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

    private String serializeIds(List<UUID> ids) {
        try { return ids == null ? null : mapper.writeValueAsString(ids.stream().map(UUID::toString).toList()); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

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

    private List<UUID> parseIds(String json) {
        if (json == null) return List.of();
        try {
            List<String> raw = mapper.readValue(json, new TypeReference<List<String>>() { });
            return raw.stream().map(UUID::fromString).toList();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}