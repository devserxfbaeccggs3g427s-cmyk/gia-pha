package com.familya.media.adapter.out.persistence;

import com.familya.media.application.port.out.MediaRepository;
import com.familya.media.domain.model.MediaAsset;
import com.familya.media.domain.model.MediaAsset.Kind;
import com.familya.media.domain.model.MediaAsset.Status;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class JdbcMediaRepository implements MediaRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcMediaRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(MediaAsset a) {
        jdbc.update(
                "INSERT INTO media_asset (id, tree_id, album_id, owner_user_id, kind, mime_type, "
                        + "byte_size, sha256, original_filename, status, quarantine_path, promoted, "
                        + "retention_hold_until, tombstoned_at, created_at, updated_at, version) "
                        + "VALUES (:id, :tree, :album, :owner, :kind, :mime, :size, :sha, :name, "
                        + ":status, :path, :promoted, :hold, :tomb, :created, :updated, :v)",
                params(a));
        jdbc.update(
                "INSERT INTO media_quarantine (media_id, tree_id, sha256, byte_size, quarantine_path, "
                        + "scanner_verdict, infected, recorded_at) VALUES (:id, :tree, :sha, :size, :path, "
                        + "'PENDING', FALSE, :recorded)",
                new MapSqlParameterSource()
                        .addValue("id", a.id().toString())
                        .addValue("tree", a.treeId().toString())
                        .addValue("sha", a.sha256())
                        .addValue("size", a.byteSize())
                        .addValue("path", a.quarantinePath())
                        .addValue("recorded", Timestamp.from(a.createdAt())));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MediaAsset> findById(UUID id) {
        var rows = jdbc.queryForList(
                "SELECT id, tree_id, album_id, owner_user_id, kind, mime_type, byte_size, sha256, "
                        + "original_filename, status, quarantine_path, promoted, retention_hold_until, "
                        + "tombstoned_at, created_at, updated_at, version FROM media_asset WHERE id = :id",
                new MapSqlParameterSource("id", id.toString()));
        return rows.isEmpty() ? Optional.empty() : Optional.of(fromRow(rows.get(0)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MediaAsset> listByTree(UUID treeId, boolean includeTombstoned) {
        String sql = includeTombstoned
                ? "SELECT * FROM media_asset WHERE tree_id = :t ORDER BY updated_at DESC"
                : "SELECT * FROM media_asset WHERE tree_id = :t AND tombstoned_at IS NULL ORDER BY updated_at DESC";
        var rows = jdbc.queryForList(sql, new MapSqlParameterSource("t", treeId.toString()));
        return rows.stream().map(this::fromRow).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MediaAsset> listByAlbum(UUID albumId, boolean includeTombstoned) {
        String sql = includeTombstoned
                ? "SELECT * FROM media_asset WHERE album_id = :a ORDER BY updated_at DESC"
                : "SELECT * FROM media_asset WHERE album_id = :a AND tombstoned_at IS NULL ORDER BY updated_at DESC";
        var rows = jdbc.queryForList(sql, new MapSqlParameterSource("a", albumId.toString()));
        return rows.stream().map(this::fromRow).toList();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void update(MediaAsset a) {
        jdbc.update(
                "UPDATE media_asset SET album_id = :album, status = :status, quarantine_path = :path, "
                        + "promoted = :promoted, retention_hold_until = :hold, tombstoned_at = :tomb, "
                        + "updated_at = :updated, version = :v, sha256 = :sha, byte_size = :size, "
                        + "mime_type = :mime, original_filename = :name WHERE id = :id",
                params(a));
        jdbc.update(
                "UPDATE media_quarantine SET scanner_verdict = :verdict, infected = :infected "
                        + "WHERE media_id = :id",
                new MapSqlParameterSource()
                        .addValue("verdict", verdictFromStatus(a.status()))
                        .addValue("infected", a.status() == Status.FAILED)
                        .addValue("id", a.id().toString()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void tombstone(UUID id, Instant at, long expectedVersion) {
        jdbc.update(
                "UPDATE media_asset SET status = 'TOMBSTONED', tombstoned_at = :at, "
                        + "updated_at = :at, version = version + 1 WHERE id = :id AND version = :v",
                new MapSqlParameterSource()
                        .addValue("at", Timestamp.from(at))
                        .addValue("id", id.toString())
                        .addValue("v", expectedVersion));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MediaAsset> listAfter(Instant watermark, int limit) {
        var rows = jdbc.queryForList(
                "SELECT * FROM media_asset WHERE updated_at >= :w ORDER BY updated_at ASC LIMIT :lim",
                new MapSqlParameterSource()
                        .addValue("w", Timestamp.from(watermark == null ? Instant.EPOCH : watermark))
                        .addValue("lim", limit));
        return rows.stream().map(this::fromRow).toList();
    }

    private MapSqlParameterSource params(MediaAsset a) {
        return new MapSqlParameterSource()
                .addValue("id", a.id().toString())
                .addValue("tree", a.treeId().toString())
                .addValue("album", a.albumId() == null ? null : a.albumId().toString())
                .addValue("owner", a.ownerUserId().toString())
                .addValue("kind", a.kind().name())
                .addValue("mime", a.mimeType())
                .addValue("size", a.byteSize())
                .addValue("sha", a.sha256())
                .addValue("name", a.originalFilename())
                .addValue("status", a.status().name())
                .addValue("path", a.quarantinePath())
                .addValue("promoted", a.promoted())
                .addValue("hold", a.retentionHoldUntil() == null ? null : Timestamp.from(a.retentionHoldUntil()))
                .addValue("tomb", a.tombstonedAt() == null ? null : Timestamp.from(a.tombstonedAt()))
                .addValue("created", Timestamp.from(a.createdAt()))
                .addValue("updated", Timestamp.from(a.updatedAt()))
                .addValue("v", a.version());
    }

    private MediaAsset fromRow(Map<String, Object> r) {
        return new MediaAsset(
                UUID.fromString((String) r.get("id")),
                UUID.fromString((String) r.get("tree_id")),
                r.get("album_id") == null ? null : UUID.fromString((String) r.get("album_id")),
                UUID.fromString((String) r.get("owner_user_id")),
                Kind.valueOf((String) r.get("kind")),
                (String) r.get("mime_type"),
                ((Number) r.get("byte_size")).longValue(),
                (String) r.get("sha256"),
                (String) r.get("original_filename"),
                Status.valueOf((String) r.get("status")),
                (String) r.get("quarantine_path"),
                Boolean.TRUE.equals(r.get("promoted")),
                r.get("retention_hold_until") == null ? null : ((Timestamp) r.get("retention_hold_until")).toInstant(),
                r.get("tombstoned_at") == null ? null : ((Timestamp) r.get("tombstoned_at")).toInstant(),
                ((Timestamp) r.get("created_at")).toInstant(),
                ((Timestamp) r.get("updated_at")).toInstant(),
                ((Number) r.get("version")).longValue());
    }

    private static String verdictFromStatus(Status s) {
        return switch (s) {
            case QUARANTINED -> "PENDING";
            case SCANNING -> "SCANNING";
            case READY -> "CLEAN";
            case FAILED -> "INFECTED";
            case TOMBSTONED -> "CLEAN";
        };
    }
}
