package com.familya.media.adapter.out.projection;

import com.familya.media.application.port.out.MediaAuthRepository;
import com.familya.media.application.port.out.ReferenceAvailability;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Adapter đầu ra (outbound) — đọc các projection nội bộ của media-service.
 * <p>
 * Triển khai hai port:
 * <ul>
 *   <li>{@link MediaAuthRepository} — đọc/ghi {@code authorization_projection}
 *       cho {@link com.familya.media.adapter.out.authorization.ProjectionMediaAuthorization}.</li>
 *   <li>{@link ReferenceAvailability} — fail-closed kiểm tra target (member/event/album)
 *       trước khi {@code AssociateMediaUseCase} gắn media.</li>
 * </ul>
 */
@Component
public class JdbcAuthRepository implements MediaAuthRepository, ReferenceAvailability {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Khởi tạo repository.
     *
     * @param jdbc JDBC template.
     */
    public JdbcAuthRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // --- MediaAuthRepository ---

    /**
     * Tra cứu row auth projection cho (tree, user).
     *
     * @param treeId UUID cây.
     * @param userId UUID người dùng.
     * @return Optional chứa {@link MediaAuthRepository.MediaAuthRow}, empty nếu chưa có.
     */
    @Override
    public Optional<MediaAuthRepository.MediaAuthRow> findAuth(UUID treeId, UUID userId) {
        var rows = jdbc.queryForList(
                "SELECT tree_id, user_id, role, revision, epoch, granted_at, revoked, source_event_id, last_updated_at "
                        + "FROM authorization_projection WHERE tree_id = :t AND user_id = :u",
                new MapSqlParameterSource().addValue("t", treeId.toString()).addValue("u", userId.toString()));
        if (rows.isEmpty()) return Optional.empty();
        var r = rows.get(0);
        return Optional.of(new MediaAuthRepository.MediaAuthRow(
                UUID.fromString((String) r.get("tree_id")),
                UUID.fromString((String) r.get("user_id")),
                (String) r.get("role"),
                ((Number) r.get("revision")).longValue(),
                ((Number) r.get("epoch")).longValue(),
                ((java.sql.Timestamp) r.get("granted_at")).toInstant(),
                Boolean.TRUE.equals(r.get("revoked")),
                (String) r.get("source_event_id"),
                ((java.sql.Timestamp) r.get("last_updated_at")).toInstant()));
    }

    /**
     * Upsert row auth projection dựa trên {@code (tree, user)} primary key.
     *
     * @param row bản ghi auth mới.
     */
    @Override
    public void upsertAuth(MediaAuthRepository.MediaAuthRow row) {
        jdbc.update(
                "INSERT INTO authorization_projection "
                        + "(tree_id, user_id, role, revision, epoch, granted_at, revoked, source_event_id, last_updated_at) "
                        + "VALUES (:t, :u, :role, :rev, :epoch, :at, :revoked, :src, :upd) "
                        + "ON DUPLICATE KEY UPDATE role = VALUES(role), revision = VALUES(revision), "
                        + "epoch = VALUES(epoch), revoked = VALUES(revoked), "
                        + "source_event_id = VALUES(source_event_id), last_updated_at = VALUES(last_updated_at)",
                new MapSqlParameterSource()
                        .addValue("t", row.treeId().toString())
                        .addValue("u", row.userId().toString())
                        .addValue("role", row.role())
                        .addValue("rev", row.revision())
                        .addValue("epoch", row.epoch())
                        .addValue("at", java.sql.Timestamp.from(row.grantedAt()))
                        .addValue("revoked", row.revoked())
                        .addValue("src", row.sourceEventId())
                        .addValue("upd", java.sql.Timestamp.from(row.lastUpdatedAt())));
    }

    // --- ReferenceAvailability ---

    /**
     * Kiểm tra member có tồn tại (và chưa bị tombstone) trong cây.
     * <p>
     * Fail-closed: nếu không có row projection thì coi như không khả dụng.
     *
     * @param treeId   UUID cây.
     * @param memberId UUID member.
     * @return true nếu exists=true và tombstoned=false.
     */
    @Override
    public boolean isMemberAvailable(UUID treeId, UUID memberId) {
        var rows = jdbc.queryForList(
                "SELECT exists, tombstoned FROM member_reference_projection "
                        + "WHERE tree_id = :t AND member_id = :m",
                new MapSqlParameterSource().addValue("t", treeId.toString()).addValue("m", memberId.toString()));
        if (rows.isEmpty()) return false;
        // Cả exists=true và tombstoned=false mới coi là khả dụng.
        return Boolean.TRUE.equals(rows.get(0).get("exists"))
                && !Boolean.TRUE.equals(rows.get(0).get("tombstoned"));
    }

    /**
     * Kiểm tra event có tồn tại (và chưa tombstone).
     *
     * @param treeId  UUID cây.
     * @param eventId UUID event.
     * @return true nếu exists=true và tombstoned=false.
     */
    @Override
    public boolean isEventAvailable(UUID treeId, UUID eventId) {
        var rows = jdbc.queryForList(
                "SELECT exists, tombstoned FROM event_reference_projection "
                        + "WHERE tree_id = :t AND event_id = :e",
                new MapSqlParameterSource().addValue("t", treeId.toString()).addValue("e", eventId.toString()));
        if (rows.isEmpty()) return false;
        return Boolean.TRUE.equals(rows.get(0).get("exists"))
                && !Boolean.TRUE.equals(rows.get(0).get("tombstoned"));
    }

    /**
     * Kiểm tra album có khả dụng (chưa tombstone) — đọc trực tiếp bảng {@code album}
     * thay vì projection.
     *
     * @param treeId  UUID cây.
     * @param albumId UUID album.
     * @return true nếu album tồn tại và chưa tombstone.
     */
    @Override
    public boolean isAlbumAvailable(UUID treeId, UUID albumId) {
        var rows = jdbc.queryForList(
                "SELECT tombstoned FROM album WHERE id = :a AND tree_id = :t",
                new MapSqlParameterSource().addValue("t", treeId.toString()).addValue("a", albumId.toString()));
        if (rows.isEmpty()) return false;
        return !Boolean.TRUE.equals(rows.get(0).get("tombstoned"));
    }
}
