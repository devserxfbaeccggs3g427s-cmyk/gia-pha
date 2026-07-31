package com.familya.treeaccess.adapter.out.persistence;

import com.familya.treeaccess.application.port.out.TreeRepository;
import com.familya.treeaccess.domain.model.AuthorizationProjection;
import com.familya.treeaccess.domain.model.Tree;
import com.familya.treeaccess.domain.model.TreeMembership;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Kho lưu trữ JDBC cho các bảng {@code tree}, {@code tree_membership} và
 * {@code authorization_projection}. Các phương thức ghi đều có propagation
 * {@link Propagation#MANDATORY} để bắt buộc caller phải mở transaction.
 */
@Component
public class JdbcTreeRepository implements TreeRepository {

    /** Template JDBC được Spring cấu hình sẵn. */
    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Khởi tạo kho lưu trữ.
     *
     * @param jdbc template JDBC
     */
    public JdbcTreeRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Chèn một cây mới cùng dòng membership của chủ sở hữu.
     *
     * @param tree            thực thể cây cần chèn
     * @param ownerMembership dòng membership ADMIN của chủ sở hữu
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void insertTree(Tree tree, TreeMembership ownerMembership) {
        jdbc.update(
                "INSERT INTO tree (id, name, owner_user_id, state, revision, epoch, created_at, version) "
                        + "VALUES (:id, :name, :owner, :state, :rev, :epoch, :created, :v)",
                treeParams(tree));
        insertMembership(ownerMembership);
    }

    /**
     * Tìm một cây theo mã {@code treeId}.
     *
     * @param treeId mã cây
     * @return {@link Optional} chứa {@link Tree} nếu tồn tại
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Tree> findTree(UUID treeId) {
        var rows = jdbc.queryForList(
                "SELECT id, name, owner_user_id, state, revision, epoch, created_at, frozen_at, tombstoned_at, version "
                        + "FROM tree WHERE id = :id",
                new MapSqlParameterSource("id", treeId.toString()));
        if (rows.isEmpty()) return Optional.empty();
        return Optional.of(treeFromRow(rows.get(0)));
    }

    /**
     * Lấy tất cả cây do một người dùng sở hữu, sắp xếp theo thời điểm tạo giảm dần.
     *
     * @param ownerUserId UUID chủ sở hữu
     * @return danh sách cây
     */
    @Override
    @Transactional(readOnly = true)
    public List<Tree> findTreesByOwner(UUID ownerUserId) {
        var rows = jdbc.queryForList(
                "SELECT id, name, owner_user_id, state, revision, epoch, created_at, frozen_at, tombstoned_at, version "
                        + "FROM tree WHERE owner_user_id = :o ORDER BY created_at DESC",
                new MapSqlParameterSource("o", ownerUserId.toString()));
        return rows.stream().map(this::treeFromRow).toList();
    }

    /**
     * Cập nhật thực thể cây (bao gồm revision, epoch, trạng thái, timestamps).
     *
     * @param tree thực thể cây đã được mutate ở tầng trên
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void updateTree(Tree tree) {
        jdbc.update(
                "UPDATE tree SET name = :name, state = :state, revision = :rev, epoch = :epoch, "
                        + "frozen_at = :frozen, tombstoned_at = :tomb, version = :v WHERE id = :id",
                treeParams(tree));
    }

    /**
     * Chèn một dòng membership mới. Nếu membership đã thu hồi, caller sẽ gọi
     * {@link #updateMembership} thay thế.
     *
     * @param m thực thể membership cần chèn
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void insertMembership(TreeMembership m) {
        jdbc.update(
                "INSERT INTO tree_membership (id, tree_id, user_id, role, granted_by, granted_at, revoked_at, revoked_by, revocation_reason) "
                        + "VALUES (:id, :tree, :user, :role, :by, :at, :revokedAt, :revokedBy, :reason)",
                new MapSqlParameterSource()
                        .addValue("id", m.id().toString())
                        .addValue("tree", m.treeId().toString())
                        .addValue("user", m.userId().toString())
                        .addValue("role", m.role().name())
                        .addValue("by", m.grantedBy().toString())
                        .addValue("at", Timestamp.from(m.grantedAt()))
                        .addValue("revokedAt", m.revokedAt() == null ? null : Timestamp.from(m.revokedAt()))
                        .addValue("revokedBy", m.revokedBy() == null ? null : m.revokedBy().toString())
                        .addValue("reason", m.revocationReason()));
    }

    /**
     * Lấy membership mới nhất của một người dùng trên một cây.
     *
     * @param treeId mã cây
     * @param userId mã người dùng
     * @return {@link Optional} chứa {@link TreeMembership} nếu có
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TreeMembership> findMembership(UUID treeId, UUID userId) {
        var rows = jdbc.queryForList(
                "SELECT id, tree_id, user_id, role, granted_by, granted_at, revoked_at, revoked_by, revocation_reason "
                        + "FROM tree_membership WHERE tree_id = :t AND user_id = :u "
                        + "ORDER BY granted_at DESC LIMIT 1",
                new MapSqlParameterSource().addValue("t", treeId.toString()).addValue("u", userId.toString()));
        if (rows.isEmpty()) return Optional.empty();
        return Optional.of(membershipFromRow(rows.get(0)));
    }

    /**
     * Lấy tất cả membership của một cây, sắp xếp theo {@code granted_at}.
     *
     * @param treeId mã cây
     * @return danh sách membership (kể cả đã thu hồi)
     */
    @Override
    @Transactional(readOnly = true)
    public List<TreeMembership> listMemberships(UUID treeId) {
        var rows = jdbc.queryForList(
                "SELECT id, tree_id, user_id, role, granted_by, granted_at, revoked_at, revoked_by, revocation_reason "
                        + "FROM tree_membership WHERE tree_id = :t ORDER BY granted_at",
                new MapSqlParameterSource("t", treeId.toString()));
        return rows.stream().map(this::membershipFromRow).toList();
    }

    /**
     * Cập nhật dòng membership (thường dùng khi tái kích hoạt hoặc thu hồi).
     *
     * @param m thực thể membership sau khi mutate
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void updateMembership(TreeMembership m) {
        jdbc.update(
                "UPDATE tree_membership SET role = :role, revoked_at = :revokedAt, revoked_by = :revokedBy, revocation_reason = :reason "
                        + "WHERE id = :id",
                new MapSqlParameterSource()
                        .addValue("role", m.role().name())
                        .addValue("revokedAt", m.revokedAt() == null ? null : Timestamp.from(m.revokedAt()))
                        .addValue("revokedBy", m.revokedBy() == null ? null : m.revokedBy().toString())
                        .addValue("reason", m.revocationReason())
                        .addValue("id", m.id().toString()));
    }

    /**
     * Upsert một hàng projection phân quyền. Khoá chính là {@code (tree_id, user_id)}.
     *
     * @param p projection cần lưu
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void upsertProjection(AuthorizationProjection p) {
        jdbc.update(
                "INSERT INTO authorization_projection "
                        + "(tree_id, user_id, role, revision, epoch, granted_at, revoked, source_event_id, last_updated_at) "
                        + "VALUES (:tree, :user, :role, :rev, :epoch, :at, :revoked, :src, :upd) "
                        + "ON DUPLICATE KEY UPDATE role = VALUES(role), revision = VALUES(revision), "
                        + "epoch = VALUES(epoch), revoked = VALUES(revoked), source_event_id = VALUES(source_event_id), "
                        + "last_updated_at = VALUES(last_updated_at)",
                new MapSqlParameterSource()
                        .addValue("tree", p.treeId().toString())
                        .addValue("user", p.userId().toString())
                        .addValue("role", p.role() == null ? null : p.role().name())
                        .addValue("rev", p.revision())
                        .addValue("epoch", p.epoch())
                        .addValue("at", Timestamp.from(p.grantedAt()))
                        .addValue("revoked", p.revoked())
                        .addValue("src", p.sourceEventId())
                        .addValue("upd", Timestamp.from(p.lastUpdatedAt())));
    }

    /**
     * Tìm projection phân quyền của một người dùng trên cây.
     *
     * @param treeId mã cây
     * @param userId mã người dùng
     * @return {@link Optional} chứa {@link AuthorizationProjection} nếu tìm thấy
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<AuthorizationProjection> findProjection(UUID treeId, UUID userId) {
        var rows = jdbc.queryForList(
                "SELECT tree_id, user_id, role, revision, epoch, granted_at, revoked, source_event_id, last_updated_at "
                        + "FROM authorization_projection WHERE tree_id = :t AND user_id = :u",
                new MapSqlParameterSource().addValue("t", treeId.toString()).addValue("u", userId.toString()));
        if (rows.isEmpty()) return Optional.empty();
        var r = rows.get(0);
        String role = (String) r.get("role");
        return Optional.of(new AuthorizationProjection(
                UUID.fromString((String) r.get("tree_id")),
                UUID.fromString((String) r.get("user_id")),
                role == null ? null : TreeMembership.Role.valueOf(role),
                ((Number) r.get("revision")).longValue(),
                ((Number) r.get("epoch")).longValue(),
                ((Timestamp) r.get("granted_at")).toInstant(),
                Boolean.TRUE.equals(r.get("revoked")),
                (String) r.get("source_event_id"),
                ((Timestamp) r.get("last_updated_at")).toInstant()));
    }

    /**
     * Lấy revision hiện tại của cây. Trả về {@code 0} nếu cây không tồn tại.
     *
     * @param treeId mã cây
     * @return revision hiện tại hoặc {@code 0}
     */
    @Override
    @Transactional(readOnly = true)
    public long currentRevision(UUID treeId) {
        var rows = jdbc.queryForList(
                "SELECT revision FROM tree WHERE id = :id",
                new MapSqlParameterSource("id", treeId.toString()));
        return rows.isEmpty() ? 0L : ((Number) rows.get(0).get("revision")).longValue();
    }

    /**
     * Tạo {@link MapSqlParameterSource} từ một {@link Tree} để truyền cho truy vấn.
     *
     * @param tree thực thể cây
     * @return tham số có khoá trùng với placeholder trong câu SQL
     */
    private MapSqlParameterSource treeParams(Tree tree) {
        return new MapSqlParameterSource()
                .addValue("id", tree.id().toString())
                .addValue("name", tree.name())
                .addValue("owner", tree.ownerUserId().toString())
                .addValue("state", tree.state().name())
                .addValue("rev", tree.revision())
                .addValue("epoch", tree.epoch())
                .addValue("created", Timestamp.from(tree.createdAt()))
                .addValue("frozen", tree.frozenAt() == null ? null : Timestamp.from(tree.frozenAt()))
                .addValue("tomb", tree.tombstonedAt() == null ? null : Timestamp.from(tree.tombstonedAt()))
                .addValue("v", tree.version());
    }

    /**
     * Chuyển một dòng kết quả thành {@link Tree}.
     *
     * @param r một dòng từ {@code queryForList}
     * @return thực thể cây tương ứng
     */
    private Tree treeFromRow(java.util.Map<String, Object> r) {
        return new Tree(
                UUID.fromString((String) r.get("id")),
                (String) r.get("name"),
                UUID.fromString((String) r.get("owner_user_id")),
                Tree.State.valueOf((String) r.get("state")),
                ((Number) r.get("revision")).longValue(),
                ((Number) r.get("epoch")).longValue(),
                ((Timestamp) r.get("created_at")).toInstant(),
                r.get("frozen_at") == null ? null : ((Timestamp) r.get("frozen_at")).toInstant(),
                r.get("tombstoned_at") == null ? null : ((Timestamp) r.get("tombstoned_at")).toInstant(),
                ((Number) r.get("version")).longValue());
    }

    /**
     * Chuyển một dòng kết quả thành {@link TreeMembership}.
     *
     * @param r một dòng từ {@code queryForList}
     * @return thực thể membership tương ứng
     */
    private TreeMembership membershipFromRow(java.util.Map<String, Object> r) {
        return new TreeMembership(
                UUID.fromString((String) r.get("id")),
                UUID.fromString((String) r.get("tree_id")),
                UUID.fromString((String) r.get("user_id")),
                TreeMembership.Role.valueOf((String) r.get("role")),
                UUID.fromString((String) r.get("granted_by")),
                ((Timestamp) r.get("granted_at")).toInstant(),
                r.get("revoked_at") == null ? null : ((Timestamp) r.get("revoked_at")).toInstant(),
                r.get("revoked_by") == null ? null : UUID.fromString((String) r.get("revoked_by")),
                (String) r.get("revocation_reason"));
    }
}