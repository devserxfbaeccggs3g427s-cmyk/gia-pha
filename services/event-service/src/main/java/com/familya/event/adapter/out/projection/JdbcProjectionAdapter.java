package com.familya.event.adapter.out.projection;

import com.familya.event.application.port.out.EventAuthRepository;
import com.familya.event.application.port.out.ReferenceAvailability;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Triển khai đồng thời hai cổng ra:
 * <ul>
 *   <li>{@link EventAuthRepository} — truy vấn/cập nhật
 *       {@code authorization_projection}.</li>
 *   <li>{@link ReferenceAvailability} — kiểm tra tham chiếu tới
 *       thành viên và media trong các projection.</li>
 * </ul>
 *
 * <p>Cả hai dựa trên cùng {@link NamedParameterJdbcTemplate} và bảng
 * projection đã được {@link com.familya.event.adapter.in.kafka.ProjectionConsumer}
 * cập nhật.
 *
 * @author gia-pha platform
 */
@Component
public class JdbcProjectionAdapter implements EventAuthRepository, ReferenceAvailability {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Khởi tạo adapter.
     *
     * @param jdbc JDBC template dùng chung.
     */
    public JdbcProjectionAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ===== EventAuthRepository =====

    /**
     * Tra cứu bản ghi phân quyền theo cặp {@code (treeId, userId)}.
     *
     * @param treeId cây gia phả.
     * @param userId người dùng.
     * @return {@link Optional} chứa {@link EventAuthRepository.EventAuthRow}
     *         hoặc rỗng.
     */
    @Override
    public Optional<EventAuthRepository.EventAuthRow> findAuth(UUID treeId, UUID userId) {
        var rows = jdbc.queryForList(
                "SELECT tree_id, user_id, role, revision, epoch, granted_at, revoked, source_event_id, last_updated_at "
                        + "FROM authorization_projection WHERE tree_id = :t AND user_id = :u",
                new MapSqlParameterSource().addValue("t", treeId.toString()).addValue("u", userId.toString()));
        if (rows.isEmpty()) return Optional.empty();
        var r = rows.get(0);
        return Optional.of(new EventAuthRepository.EventAuthRow(
                UUID.fromString((String) r.get("tree_id")),
                UUID.fromString((String) r.get("user_id")),
                (String) r.get("role"),
                ((Number) r.get("revision")).longValue(),
                ((Number) r.get("epoch")).longValue(),
                ((Timestamp) r.get("granted_at")).toInstant(),
                Boolean.TRUE.equals(r.get("revoked")),
                (String) r.get("source_event_id"),
                ((Timestamp) r.get("last_updated_at")).toInstant()));
    }

    /**
     * UPSERT bản ghi phân quyền — idempotent theo {@code (treeId, userId)}.
     *
     * @param row bản ghi cần ghi.
     */
    @Override
    public void upsertAuth(EventAuthRepository.EventAuthRow row) {
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
                        .addValue("at", Timestamp.from(row.grantedAt()))
                        .addValue("revoked", row.revoked())
                        .addValue("src", row.sourceEventId())
                        .addValue("upd", Timestamp.from(row.lastUpdatedAt())));
    }

    // ===== ReferenceAvailability =====

    /**
     * Kiểm tra thành viên còn khả dụng hay không.
     *
     * <p>Một thành viên được coi là <i>khả dụng</i> khi và chỉ khi
     * {@code exists=true} và {@code tombstoned=false}.
     *
     * @param treeId   cây gia phả.
     * @param memberId ID thành viên.
     * @return {@code true} nếu khả dụng.
     */
    @Override
    public boolean isMemberAvailable(UUID treeId, UUID memberId) {
        var rows = jdbc.queryForList(
                "SELECT exists, tombstoned FROM member_reference_projection "
                        + "WHERE tree_id = :t AND member_id = :m",
                new MapSqlParameterSource().addValue("t", treeId.toString()).addValue("m", memberId.toString()));
        if (rows.isEmpty()) return false;
        return Boolean.TRUE.equals(rows.get(0).get("exists"))
                && !Boolean.TRUE.equals(rows.get(0).get("tombstoned"));
    }

    /**
     * Tương tự {@link #isMemberAvailable} nhưng cho media.
     *
     * @param treeId  cây gia phả.
     * @param mediaId ID media.
     * @return {@code true} nếu media khả dụng.
     */
    @Override
    public boolean isMediaAvailable(UUID treeId, UUID mediaId) {
        var rows = jdbc.queryForList(
                "SELECT exists, tombstoned FROM media_reference_projection "
                        + "WHERE tree_id = :t AND media_id = :m",
                new MapSqlParameterSource().addValue("t", treeId.toString()).addValue("m", mediaId.toString()));
        if (rows.isEmpty()) return false;
        return Boolean.TRUE.equals(rows.get(0).get("exists"))
                && !Boolean.TRUE.equals(rows.get(0).get("tombstoned"));
    }

    /**
     * Trả về tập con các {@code memberIds} không khả dụng.
     *
     * <p>Lưu ý: tập kết quả giữ thứ tự xuất hiện nhờ {@link LinkedHashSet}.
     *
     * @param treeId    cây gia phả.
     * @param memberIds danh sách cần kiểm tra.
     * @return tập thành viên đứt.
     */
    @Override
    public Set<UUID> danglingMembers(UUID treeId, Collection<UUID> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) return Set.of();
        return memberIds.stream()
                .filter(id -> id != null && !isMemberAvailable(treeId, id))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Tương tự {@link #danglingMembers} nhưng cho media.
     *
     * @param treeId   cây gia phả.
     * @param mediaIds danh sách cần kiểm tra.
     * @return tập media đứt.
     */
    @Override
    public Set<UUID> danglingMedia(UUID treeId, Collection<UUID> mediaIds) {
        if (mediaIds == null || mediaIds.isEmpty()) return Set.of();
        return mediaIds.stream()
                .filter(id -> id != null && !isMediaAvailable(treeId, id))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
