package com.familya.sharing.adapter.out.persistence;

import com.familya.sharing.application.port.out.ShareAuthRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter JDBC hiện thực {@link ShareAuthRepository} &mdash; truy cập bảng
 * {@code authorization_projection} trong cơ sở dữ liệu chia sẻ.
 * <p>
 * Bảng này chứa projection phân quyền theo (treeId, userId), được đồng bộ từ
 * các sự kiện membership thông qua {@code SharingProjectionConsumer}.
 * <p>
 * <b>Lưu ý:</b> class này nằm trong package {@code adapter.out.persistence}
 * (không phải {@code adapter.out.authorization}) do yêu cầu cấu trúc thư mục
 * đã có sẵn của dự án.
 */
@Component
public class JdbcShareAuthRepository implements ShareAuthRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Khởi tạo adapter.
     *
     * @param jdbc template JDBC chia sẻ.
     */
    public JdbcShareAuthRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Tra cứu bản ghi phân quyền của người dùng trên một cây.
     * <p>
     * Thực thi trong transaction chỉ-đọc.
     *
     * @param treeId định danh cây gia phả.
     * @param userId định danh người dùng.
     * @return {@link Optional} chứa {@link AuthRow} nếu tồn tại.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<AuthRow> find(UUID treeId, UUID userId) {
        // Bước 1: Truy vấn các cột cần thiết.
        var rows = jdbc.queryForList(
                "SELECT tree_id, user_id, role, revision, epoch, revoked, last_updated_at "
                        + "FROM authorization_projection WHERE tree_id = :t AND user_id = :u",
                new MapSqlParameterSource()
                        .addValue("t", treeId.toString())
                        .addValue("u", userId.toString()));

        // Bước 2: Trả về Optional.empty() nếu không có.
        if (rows.isEmpty()) return Optional.empty();

        // Bước 3: Ánh xạ dòng đầu tiên sang AuthRow &mdash; ép kiểu an toàn.
        var r = rows.get(0);
        return Optional.of(new AuthRow(
                UUID.fromString((String) r.get("tree_id")),
                UUID.fromString((String) r.get("user_id")),
                (String) r.get("role"),
                ((Number) r.get("revision")).longValue(),
                ((Number) r.get("epoch")).longValue(),
                Boolean.TRUE.equals(r.get("revoked")),
                ((Timestamp) r.get("last_updated_at")).toInstant()));
    }

    /**
     * Thêm mới hoặc cập nhật bản ghi phân quyền.
     * <p>
     * Sử dụng {@code INSERT ... ON DUPLICATE KEY UPDATE} để vừa idempotent,
     * vừa cập nhật mọi cột khi đã tồn tại. Phương thức này yêu cầu phải
     * chạy trong một transaction hiện có ({@link Propagation#MANDATORY}) &mdash;
     * caller (use case) chịu trách nhiệm mở transaction.
     *
     * @param treeId         định danh cây.
     * @param userId         định danh người dùng.
     * @param role           vai trò.
     * @param revision       revision của cây.
     * @param epoch          epoch tương ứng.
     * @param grantedAt      thời điểm cấp quyền.
     * @param revoked        {@code true} nếu đã thu hồi.
     * @param sourceEventId  định danh sự kiện nguồn.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void upsert(UUID treeId, UUID userId, String role, long revision, long epoch,
                        Instant grantedAt, boolean revoked, String sourceEventId) {
        jdbc.update(
                "INSERT INTO authorization_projection (tree_id, user_id, role, revision, epoch, granted_at, revoked, source_event_id, last_updated_at) "
                        + "VALUES (:t, :u, :role, :rev, :ep, :at, :revoked, :src, :upd) "
                        + "ON DUPLICATE KEY UPDATE role = VALUES(role), revision = VALUES(revision), epoch = VALUES(epoch), "
                        + "revoked = VALUES(revoked), source_event_id = VALUES(source_event_id), last_updated_at = VALUES(last_updated_at)",
                new MapSqlParameterSource()
                        .addValue("t", treeId.toString())
                        .addValue("u", userId.toString())
                        .addValue("role", role)
                        .addValue("rev", revision)
                        .addValue("ep", epoch)
                        .addValue("at", Timestamp.from(grantedAt))
                        .addValue("revoked", revoked)
                        .addValue("src", sourceEventId)
                        .addValue("upd", Timestamp.from(Instant.now())));
    }
}