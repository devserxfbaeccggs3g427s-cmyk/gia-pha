package com.familya.search.adapter.in.rest;

import com.familya.search.application.port.out.SearchWatermarkRepository;
import com.familya.search.domain.model.Watermark;
import com.familya.platform.api.AsyncOperation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api/v2/internal/search", produces = MediaType.APPLICATION_JSON_VALUE)
public class SearchMigrationController {

    private final SearchWatermarkRepository watermark;
    private final NamedParameterJdbcTemplate jdbc;

    public SearchMigrationController(SearchWatermarkRepository watermark, NamedParameterJdbcTemplate jdbc) {
        this.watermark = watermark;
        this.jdbc = jdbc;
    }

    @PostMapping(path = "/migration/projection/{domain}/rebuild", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<AsyncOperation> rebuild(@PathVariable String domain,
                                                    @RequestHeader("X-Correlation-Id") String correlationId,
                                                    @Valid @RequestBody RebuildRequest req) {
        Watermark.Domain d = Watermark.Domain.valueOf(domain.toUpperCase());
        rebuildForDomain(d, req);
        watermark.advance(req.treeId(), d, req.watermark());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(AsyncOperation.accepted(req.treeId(), "/api/v2/operations/" + req.treeId()));
    }

    private void rebuildForDomain(Watermark.Domain domain, RebuildRequest req) {
        switch (domain) {
            case MEMBER -> upsertMemberDoc(req);
            case EVENT -> upsertEventDoc(req);
            case MEDIA -> upsertMediaDoc(req);
            case TREE -> { /* no-op: tree-level watermarks are advanced only */ }
            case RELATIONSHIP -> { /* not directly exposed; rebuild via member-side */ }
        }
    }

    private void upsertMemberDoc(RebuildRequest req) {
        jdbc.update(
                "INSERT INTO search_member_doc (tree_id, member_id, full_name, given_name, surname, birth_year, death_year, tombstoned, normalized_name, last_updated) "
                        + "VALUES (:t, :m, :name, :g, :s, :by, :dy, :tomb, :norm, :u) "
                        + "ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), given_name = VALUES(given_name), surname = VALUES(surname), "
                        + "birth_year = VALUES(birth_year), death_year = VALUES(death_year), tombstoned = VALUES(tombstoned), "
                        + "normalized_name = VALUES(normalized_name), last_updated = VALUES(last_updated)",
                memberParams(req));
    }

    private MapSqlParameterSource memberParams(RebuildRequest req) {
        return baseParams(req)
                .addValue("name", req.payload() == null ? "" : String.valueOf(req.payload().getOrDefault("fullName", "")))
                .addValue("g", req.payload() == null ? null : req.payload().get("givenName"))
                .addValue("s", req.payload() == null ? null : req.payload().get("surname"))
                .addValue("by", req.payload() == null ? null : req.payload().get("birthYear"))
                .addValue("dy", req.payload() == null ? null : req.payload().get("deathYear"))
                .addValue("tomb", Boolean.TRUE.equals(req.payload() == null ? null : req.payload().get("tombstoned")))
                .addValue("norm", req.normalizedName());
    }

    private void upsertEventDoc(RebuildRequest req) {
        jdbc.update(
                "INSERT INTO search_event_doc (tree_id, event_id, title, start_date, kind, tombstoned, normalized_title, last_updated) "
                        + "VALUES (:t, :e, :title, :d, :k, :tomb, :norm, :u) "
                        + "ON DUPLICATE KEY UPDATE title = VALUES(title), start_date = VALUES(start_date), kind = VALUES(kind), "
                        + "tombstoned = VALUES(tombstoned), normalized_title = VALUES(normalized_title), last_updated = VALUES(last_updated)",
                baseParams(req)
                        .addValue("title", req.payload() == null ? "" : String.valueOf(req.payload().getOrDefault("title", "")))
                        .addValue("d", req.payload() == null ? null : req.payload().get("startDate"))
                        .addValue("k", req.payload() == null ? null : req.payload().get("kind"))
                        .addValue("tomb", Boolean.TRUE.equals(req.payload() == null ? null : req.payload().get("tombstoned")))
                        .addValue("norm", req.normalizedName()));
    }

    private void upsertMediaDoc(RebuildRequest req) {
        jdbc.update(
                "INSERT INTO search_media_doc (tree_id, media_id, filename, kind, tombstoned, normalized_filename, last_updated) "
                        + "VALUES (:t, :m, :name, :k, :tomb, :norm, :u) "
                        + "ON DUPLICATE KEY UPDATE filename = VALUES(filename), kind = VALUES(kind), tombstoned = VALUES(tombstoned), "
                        + "normalized_filename = VALUES(normalized_filename), last_updated = VALUES(last_updated)",
                baseParams(req)
                        .addValue("name", req.payload() == null ? "" : String.valueOf(req.payload().getOrDefault("filename", "")))
                        .addValue("k", req.payload() == null ? "OTHER" : String.valueOf(req.payload().getOrDefault("kind", "OTHER")))
                        .addValue("tomb", Boolean.TRUE.equals(req.payload() == null ? null : req.payload().get("tombstoned")))
                        .addValue("norm", req.normalizedName()));
    }

    private MapSqlParameterSource baseParams(RebuildRequest req) {
        return new MapSqlParameterSource()
                .addValue("t", req.treeId().toString())
                .addValue("m", req.entityId() == null ? null : req.entityId().toString())
                .addValue("e", req.entityId() == null ? null : req.entityId().toString())
                .addValue("u", java.sql.Timestamp.from(Instant.now()));
    }

    @GetMapping("/migration/reconcile")
    public ResponseEntity<ReconcileResponse> reconcile(@RequestParam UUID treeId) {
        var barrier = watermark.barrierFor(treeId);
        return ResponseEntity.ok(new ReconcileResponse(treeId, barrier.values()));
    }

    public record RebuildRequest(
            @NotNull UUID treeId,
            UUID entityId,
            @NotNull String normalizedName,
            long watermark,
            java.util.Map<String, Object> payload) { }

    public record ReconcileResponse(UUID treeId, java.util.Map<Watermark.Domain, Long> watermarks) { }
}
