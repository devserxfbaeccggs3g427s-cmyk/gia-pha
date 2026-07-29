package com.familya.media.adapter.out.persistence;

import com.familya.media.application.port.out.AlbumRepository;
import com.familya.media.domain.model.Album;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class JdbcAlbumRepository implements AlbumRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcAlbumRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(Album a) {
        jdbc.update(
                "INSERT INTO album (id, tree_id, name, description, cover_media_id, "
                        + "created_at, updated_at, version, tombstoned_at) "
                        + "VALUES (:id, :tree, :name, :desc, :cover, :created, :updated, :v, :tomb)",
                params(a));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Album> findById(UUID id) {
        var rows = jdbc.queryForList(
                "SELECT * FROM album WHERE id = :id",
                new MapSqlParameterSource("id", id.toString()));
        return rows.isEmpty() ? Optional.empty() : Optional.of(fromRow(rows.get(0)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Album> listByTree(UUID treeId, boolean includeTombstoned) {
        String sql = includeTombstoned
                ? "SELECT * FROM album WHERE tree_id = :t ORDER BY updated_at DESC"
                : "SELECT * FROM album WHERE tree_id = :t AND tombstoned_at IS NULL ORDER BY updated_at DESC";
        var rows = jdbc.queryForList(sql, new MapSqlParameterSource("t", treeId.toString()));
        return rows.stream().map(this::fromRow).toList();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void update(Album a) {
        jdbc.update(
                "UPDATE album SET name = :name, description = :desc, cover_media_id = :cover, "
                        + "updated_at = :updated, version = :v, tombstoned_at = :tomb WHERE id = :id",
                params(a));
    }

    private MapSqlParameterSource params(Album a) {
        return new MapSqlParameterSource()
                .addValue("id", a.id().toString())
                .addValue("tree", a.treeId().toString())
                .addValue("name", a.name())
                .addValue("desc", a.description())
                .addValue("cover", a.coverMediaId() == null ? null : a.coverMediaId().toString())
                .addValue("created", Timestamp.from(a.createdAt()))
                .addValue("updated", Timestamp.from(a.updatedAt()))
                .addValue("v", a.version())
                .addValue("tomb", a.tombstonedAt() == null ? null : Timestamp.from(a.tombstonedAt()));
    }

    private Album fromRow(Map<String, Object> r) {
        return new Album(
                UUID.fromString((String) r.get("id")),
                UUID.fromString((String) r.get("tree_id")),
                (String) r.get("name"),
                (String) r.get("description"),
                r.get("cover_media_id") == null ? null : UUID.fromString((String) r.get("cover_media_id")),
                ((Timestamp) r.get("created_at")).toInstant(),
                ((Timestamp) r.get("updated_at")).toInstant(),
                ((Number) r.get("version")).longValue(),
                r.get("tombstoned_at") == null ? null : ((Timestamp) r.get("tombstoned_at")).toInstant());
    }
}
