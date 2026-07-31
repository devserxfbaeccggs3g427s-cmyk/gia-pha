package com.familya.sharing.adapter.out.persistence;

import com.familya.sharing.application.port.out.ShareLinkRepository;
import com.familya.sharing.domain.model.ShareLink;
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
 * Adapter JDBC hiện thực {@link ShareLinkRepository} &mdash; truy cập bảng
 * {@code share_link} trong cơ sở dữ liệu.
 * <p>
 * Các phương thức ghi (insert/update) yêu cầu caller đã mở transaction
 * ({@link Propagation#MANDATORY}), trong khi các phương thức đọc dùng
 * transaction chỉ-đọc.
 */
@Component
public class JdbcShareLinkRepository implements ShareLinkRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Khởi tạo adapter.
     *
     * @param jdbc template JDBC chia sẻ.
     */
    public JdbcShareLinkRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Thêm mới một liên kết chia sẻ.
     *
     * @param l đối tượng {@link ShareLink} cần thêm.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(ShareLink l) {
        jdbc.update(
                "INSERT INTO share_link (id, tree_id, scope, target_id, role, token_hash, created_by_user_id, "
                        + "created_at, expires_at, revoked_at, revocation_reason, revision, version) "
                        + "VALUES (:id, :tree, :scope, :target, :role, :hash, :creator, :created, :expires, :revoked, :reason, :revision, :version)",
                params(l));
    }

    /**
     * Cập nhật một liên kết &mdash; hiện chỉ cập nhật các trường thu hồi và
     * revision/version (chủ yếu dùng cho thu hồi).
     *
     * @param l đối tượng {@link ShareLink} chứa dữ liệu cập nhật.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void update(ShareLink l) {
        jdbc.update(
                "UPDATE share_link SET revoked_at = :revoked, revocation_reason = :reason, revision = :revision, version = :version WHERE id = :id",
                params(l));
    }

    /**
     * Tra cứu liên kết theo định danh.
     *
     * @param id định danh liên kết.
     * @return {@link Optional} chứa liên kết nếu tồn tại.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<ShareLink> findById(UUID id) {
        return one("SELECT * FROM share_link WHERE id = :x", Map.of("x", id.toString()));
    }

    /**
     * Tra cứu liên kết theo giá trị hash của token.
     *
     * @param hash chuỗi hash SHA-256 hex.
     * @return {@link Optional} chứa liên kết nếu tồn tại.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<ShareLink> findByTokenHash(String hash) {
        return one("SELECT * FROM share_link WHERE token_hash = :x", Map.of("x", hash));
    }

    /**
     * Liệt kê tất cả liên kết của một cây, sắp xếp theo thời điểm tạo giảm dần.
     *
     * @param id định danh cây gia phả.
     * @return danh sách liên kết (rỗng nếu không có).
     */
    @Override
    @Transactional(readOnly = true)
    public List<ShareLink> listByTree(UUID id) {
        var rows = jdbc.queryForList(
                "SELECT * FROM share_link WHERE tree_id = :x ORDER BY created_at DESC",
                Map.of("x", id.toString()));
        return rows.stream().map(this::row).toList();
    }

    /**
     * Liệt kê các liên kết đang hoạt động của một cây với phạm vi/mục tiêu
     * cụ thể. Sử dụng toán tử {@code <=>} để so sánh {@code target_id} an
     * toàn với {@code NULL}.
     *
     * @param treeId   định danh cây gia phả.
     * @param scope    phạm vi chia sẻ.
     * @param targetId định danh mục tiêu (có thể {@code null}).
     * @return danh sách liên kết đang hoạt động.
     */
    @Override
    @Transactional(readOnly = true)
    public List<ShareLink> listActiveByScope(UUID treeId, ShareLink.Scope scope, UUID targetId) {
        var rows = jdbc.queryForList(
                "SELECT * FROM share_link WHERE tree_id = :t AND scope = :s AND target_id <=> :id "
                        + "AND revoked_at IS NULL ORDER BY created_at DESC",
                new MapSqlParameterSource()
                        .addValue("t", treeId.toString())
                        .addValue("s", scope.name())
                        .addValue("id", targetId == null ? null : targetId.toString()));
        return rows.stream().map(this::row).toList();
    }

    /**
     * Hàm tiện ích: thực thi câu truy vấn và trả về một dòng kết quả (nếu có).
     *
     * @param sql    câu SQL cần thực thi.
     * @param params tham số truy vấn.
     * @return {@link Optional} chứa {@link ShareLink} hoặc rỗng.
     */
    private Optional<ShareLink> one(String sql, Map<String, Object> params) {
        var rows = jdbc.queryForList(sql, params);
        return rows.isEmpty() ? Optional.empty() : Optional.of(row(rows.get(0)));
    }

    /**
     * Đóng gói các tham số từ một {@link ShareLink} sang {@link MapSqlParameterSource}
     * để dùng cho cả {@code insert} và {@code update}.
     *
     * @param l đối tượng {@link ShareLink} nguồn.
     * @return {@link MapSqlParameterSource} tương ứng.
     */
    private MapSqlParameterSource params(ShareLink l) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("id", l.id().toString())
                .addValue("tree", l.treeId().toString())
                .addValue("scope", l.scope().name())
                .addValue("target", l.targetId() == null ? null : l.targetId().toString())
                .addValue("role", l.role().name())
                .addValue("hash", l.tokenHash())
                .addValue("creator", l.createdByUserId().toString())
                .addValue("created", Timestamp.from(l.createdAt()))
                .addValue("expires", l.expiresAt() == null ? null : Timestamp.from(l.expiresAt()))
                .addValue("revoked", l.revokedAt() == null ? null : Timestamp.from(l.revokedAt()))
                .addValue("reason", l.revocationReason())
                .addValue("revision", l.revision())
                .addValue("version", l.version());
        return p;
    }

    /**
     * Ánh xạ một dòng kết quả SQL sang {@link ShareLink}.
     *
     * @param r dòng kết quả (kiểu {@code Map<String,Object>}).
     * @return đối tượng {@link ShareLink} tương ứng.
     */
    private ShareLink row(Map<String, Object> r) {
        return new ShareLink(
                UUID.fromString((String) r.get("id")),
                UUID.fromString((String) r.get("tree_id")),
                ShareLink.Scope.valueOf((String) r.get("scope")),
                r.get("target_id") == null ? null : UUID.fromString((String) r.get("target_id")),
                ShareLink.Role.valueOf((String) r.get("role")),
                (String) r.get("token_hash"),
                UUID.fromString((String) r.get("created_by_user_id")),
                ((Timestamp) r.get("created_at")).toInstant(),
                r.get("expires_at") == null ? null : ((Timestamp) r.get("expires_at")).toInstant(),
                r.get("revoked_at") == null ? null : ((Timestamp) r.get("revoked_at")).toInstant(),
                (String) r.get("revocation_reason"),
                ((Number) r.get("revision")).longValue(),
                ((Number) r.get("version")).longValue());
    }
}