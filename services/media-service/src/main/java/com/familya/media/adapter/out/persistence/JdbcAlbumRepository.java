package com.familya.media.adapter.out.persistence;

import com.familya.media.application.port.out.AlbumRepository;
import com.familya.media.domain.model.Album;
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

/**
 * Adapter đầu ra (outbound) — repository JDBC cho {@link com.familya.media.domain.model.Album}.
 * <p>
 * Áp dụng các quy ước:
 * <ul>
 *   <li>Thao tác ghi dùng {@link Propagation#MANDATORY} — bắt buộc có transaction
 *       ngoài (do use case quản lý) để đảm bảo atomic với outbox/domain changes.</li>
 *   <li>Thao tác đọc dùng {@code readOnly = true}.</li>
 *   <li>Tombstone dùng optimistic locking: {@code UPDATE ... WHERE id = :id AND version = :v}.</li>
 * </ul>
 */
@Component
public class JdbcAlbumRepository implements AlbumRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Khởi tạo repository.
     *
     * @param jdbc JDBC template.
     */
    public JdbcAlbumRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Chèn một album mới. Phải chạy trong transaction của caller (MANDATORY).
     *
     * @param a album cần chèn.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(Album a) {
        jdbc.update(
                "INSERT INTO album (id, tree_id, name, description, cover_media_id, "
                        + "created_at, updated_at, version, tombstoned_at) "
                        + "VALUES (:id, :tree, :name, :desc, :cover, :created, :updated, :v, :tomb)",
                params(a));
    }

    /**
     * Tra cứu album theo id.
     *
     * @param id UUID album.
     * @return Optional chứa album nếu tồn tại.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Album> findById(UUID id) {
        var rows = jdbc.queryForList(
                "SELECT * FROM album WHERE id = :id",
                new MapSqlParameterSource("id", id.toString()));
        return rows.isEmpty() ? Optional.empty() : Optional.of(fromRow(rows.get(0)));
    }

    /**
     * Liệt kê album của một cây, có thể bao gồm cả các album đã tombstone.
     * <p>
     * Sắp xếp theo {@code updated_at DESC} — album cập nhật gần nhất trước.
     *
     * @param treeId            UUID cây.
     * @param includeTombstoned nếu true bao gồm cả album đã tombstone.
     * @return danh sách album.
     */
    @Override
    @Transactional(readOnly = true)
    public List<Album> listByTree(UUID treeId, boolean includeTombstoned) {
        String sql = includeTombstoned
                ? "SELECT * FROM album WHERE tree_id = :t ORDER BY updated_at DESC"
                : "SELECT * FROM album WHERE tree_id = :t AND tombstoned_at IS NULL ORDER BY updated_at DESC";
        var rows = jdbc.queryForList(sql, new MapSqlParameterSource("t", treeId.toString()));
        return rows.stream().map(this::fromRow).toList();
    }

    /**
     * Cập nhật một album (caller đã tăng version). Phải chạy trong transaction của caller.
     *
     * @param a album với version mới.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void update(Album a) {
        jdbc.update(
                "UPDATE album SET name = :name, description = :desc, cover_media_id = :cover, "
                        + "updated_at = :updated, version = :v, tombstoned_at = :tomb WHERE id = :id",
                params(a));
    }

    /** Helper chuyển {@link Album} sang MapSqlParameterSource. */
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

    /**
     * Tombstone album với optimistic locking.
     * <p>
     * SQL: {@code UPDATE ... WHERE id = :id AND version = :v}. Nếu version không
     * khớp, số dòng affected = 0 và use case sẽ ném {@code OptimisticLockException}.
     *
     * @param id              UUID album.
     * @param at              thời điểm tombstone.
     * @param expectedVersion version kỳ vọng.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void tombstone(UUID id, Instant at, long expectedVersion) {
        // Optimistic lock: chỉ update khi version còn khớp.
        jdbc.update(
                "UPDATE album SET tombstoned_at = :t, updated_at = :u, version = version + 1 "
                        + "WHERE id = :id AND version = :v",
                new MapSqlParameterSource()
                        .addValue("t", Timestamp.from(at))
                        .addValue("u", Timestamp.from(at))
                        .addValue("id", id.toString())
                        .addValue("v", expectedVersion));
    }

    /** Helper chuyển row SQL sang {@link Album}. */
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
