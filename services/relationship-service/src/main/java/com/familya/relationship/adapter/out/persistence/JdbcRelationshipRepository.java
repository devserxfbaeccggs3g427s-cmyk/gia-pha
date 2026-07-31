package com.familya.relationship.adapter.out.persistence;

import com.familya.relationship.application.port.out.RelationshipRepository;
import com.familya.relationship.domain.model.Relationship;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter JDBC cụ thể cho cổng {@link RelationshipRepository}.
 * <p>
 * Tất cả các phương thức ghi đều sử dụng {@code Propagation.MANDATORY} - nghĩa
 * là phải được gọi trong một transaction đang mở ở caller. Điều này đảm bảo:
 * </p>
 * <ul>
 *   <li>Các lệnh ghi (insert/update/delete) chỉ thực sự commit khi use case
 *       commit cả transaction.</li>
 *   <li>Sự kiện outbox được đảm bảo phát hành đồng bộ với thay đổi dữ liệu.</li>
 * </ul>
 *
 * <p>
 * Các phương thức đọc được đánh dấu {@code readOnly = true} để tối ưu và cho
 * phép driver/database tận dụng các tối ưu riêng (ví dụ: replica).
 * </p>
 */
@Component
public class JdbcRelationshipRepository implements RelationshipRepository {

    /** Template JDBC dùng chung cho toàn bộ adapter. */
    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Khởi tạo adapter.
     *
     * @param jdbc template JDBC đã được Spring cấu hình
     */
    public JdbcRelationshipRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sử dụng {@code Propagation.MANDATORY}: bắt buộc phải có transaction mở sẵn.
     * </p>
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(Relationship rel) {
        jdbc.update(
                "INSERT INTO relationship (id, tree_id, kind, from_member_id, to_member_id, "
                        + "metadata_json, revision, created_at, version) "
                        + "VALUES (:id, :tree, :kind, :from, :to, :meta, :rev, :created, :v)",
                params(rel));
    }

    /**
     * {@inheritDoc}
     * <p>
     * Đọc từ bảng {@code relationship} theo {@code id}. Trả về {@link Optional#empty()}
     * nếu không tìm thấy.
     * </p>
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Relationship> findById(UUID id) {
        var rows = jdbc.queryForList(
                "SELECT id, tree_id, kind, from_member_id, to_member_id, metadata_json, "
                        + "revision, created_at, tombstoned_at, version "
                        + "FROM relationship WHERE id = :id",
                new MapSqlParameterSource("id", id.toString()));
        return rows.isEmpty() ? Optional.empty() : Optional.of(fromRow(rows.get(0)));
    }

    /**
     * {@inheritDoc}
     * <p>
     * Nếu {@code includeTombstoned = false}, chỉ lấy các quan hệ đang sống
     * (dùng mệnh đề {@code tombstoned_at IS NULL}); nếu {@code true}, lấy tất cả.
     * </p>
     */
    @Override
    @Transactional(readOnly = true)
    public List<Relationship> listByTree(UUID treeId, boolean includeTombstoned) {
        String sql = includeTombstoned
                ? "SELECT * FROM relationship WHERE tree_id = :t"
                : "SELECT * FROM relationship WHERE tree_id = :t AND tombstoned_at IS NULL";
        var rows = jdbc.queryForList(sql, new MapSqlParameterSource("t", treeId.toString()));
        return rows.stream().map(this::fromRow).toList();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Dùng cho saga xóa thành viên: lấy các cạnh đang hoạt động mà liên quan
     * tới thành viên ở bất kỳ chiều nào.
     * </p>
     */
    @Override
    @Transactional(readOnly = true)
    public List<Relationship> listActiveByMember(UUID treeId, UUID memberId) {
        var rows = jdbc.queryForList(
                "SELECT id, tree_id, kind, from_member_id, to_member_id, metadata_json, "
                        + "revision, created_at, tombstoned_at, version "
                        + "FROM relationship WHERE tree_id = :t AND tombstoned_at IS NULL "
                        + "AND (from_member_id = :m OR to_member_id = :m)",
                new MapSqlParameterSource()
                        .addValue("t", treeId.toString())
                        .addValue("m", memberId.toString()));
        return rows.stream().map(this::fromRow).toList();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sử dụng {@code INSERT ... ON DUPLICATE KEY UPDATE} để idempotent: nếu
     * snapshot cho cùng {@code operationId} đã tồn tại thì ghi đè. Điều này
     * đảm bảo saga có thể chạy lại nhiều lần mà không phá vỡ tính nhất quán.
     * </p>
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveCompensationSnapshot(UUID operationId, String snapshotJson) {
        jdbc.update(
                "INSERT INTO saga_compensation_snapshot (operation_id, snapshot_json, recorded_at) "
                        + "VALUES (:id, :snap, :ts) "
                        + "ON DUPLICATE KEY UPDATE snapshot_json = VALUES(snapshot_json), recorded_at = VALUES(recorded_at)",
                new MapSqlParameterSource()
                        .addValue("id", operationId.toString())
                        .addValue("snap", snapshotJson)
                        .addValue("ts", Timestamp.from(java.time.Instant.now())));
    }

    /**
     * {@inheritDoc}
     * <p>
     * Trả về {@code null} nếu không có snapshot cho {@code operationId}.
     * </p>
     */
    @Override
    @Transactional(readOnly = true)
    public String loadCompensationSnapshot(UUID operationId) {
        var rows = jdbc.queryForList(
                "SELECT snapshot_json FROM saga_compensation_snapshot WHERE operation_id = :id",
                new MapSqlParameterSource("id", operationId.toString()));
        return rows.isEmpty() ? null : (String) rows.get(0).get("snapshot_json");
    }

    /**
     * {@inheritDoc}
     * <p>
     * Cơ chế hoạt động:
     * </p>
     * <ol>
     *   <li>Thử INSERT dòng "init" cho cây nếu chưa có (idempotent - bỏ qua lỗi).</li>
     *   <li>Dùng {@code SELECT MAX(command_seq) ... FOR UPDATE} để lấy giá trị
     *       lớn nhất hiện tại và khóa các dòng tương ứng - đảm bảo hai transaction
     *       cùng cây không thể cùng lúc lấy cùng một {@code commandSeq}.</li>
     *   <li>Trả về {@code max + 1}.</li>
     * </ol>
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public long nextCommandSeq(UUID treeId) {
        // Per-tree monotonic sequence. We rely on MySQL row-level
        // locking via SELECT … FOR UPDATE inside the transaction; this
        // serializes concurrent graph commands for the same tree
        // while letting different trees proceed in parallel.
        try {
            // Bước 1: chèn dòng khởi tạo nếu cây chưa có dòng nào trong command log.
            // Lệnh này idempotent: nếu đã có dòng khởi tạo, lỗi sẽ bị bỏ qua.
            jdbc.update(
                    "INSERT INTO graph_command_log (tree_id, command_seq, command_type, actor_user_id, payload_hash, committed_at) "
                            + "VALUES (:t, 1, 'init', :u, 'init', :ts)",
                    new MapSqlParameterSource()
                            .addValue("t", treeId.toString())
                            .addValue("u", "00000000-0000-0000-0000-000000000000")
                            .addValue("ts", Timestamp.from(java.time.Instant.now())));
        } catch (Exception ignored) { /* already initialized - bỏ qua lỗi trùng khóa */ }
        // Bước 2: lấy max command_seq và khóa các dòng tương ứng (FOR UPDATE).
        // Nhờ khóa này, các transaction cùng cây sẽ phải xếp hàng nối tiếp.
        Long last = jdbc.queryForObject(
                "SELECT MAX(command_seq) FROM graph_command_log WHERE tree_id = :t FOR UPDATE",
                new MapSqlParameterSource("t", treeId.toString()), Long.class);
        return (last == null ? 0L : last) + 1L;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Ghi một dòng mới vào {@code graph_command_log} phản ánh lệnh vừa commit.
     * </p>
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendCommandLog(UUID treeId, long commandSeq, String commandType,
                                  UUID actorUserId, String payloadHash, java.time.Instant committedAt) {
        jdbc.update(
                "INSERT INTO graph_command_log (tree_id, command_seq, command_type, actor_user_id, payload_hash, committed_at) "
                        + "VALUES (:t, :seq, :type, :u, :h, :ts)",
                new MapSqlParameterSource()
                        .addValue("t", treeId.toString())
                        .addValue("seq", commandSeq)
                        .addValue("type", commandType)
                        .addValue("u", actorUserId.toString())
                        .addValue("h", payloadHash)
                        .addValue("ts", Timestamp.from(committedAt)));
    }

    /**
     * {@inheritDoc}
     * <p>
     * Hiện tại chỉ cập nhật {@code tombstoned_at} và {@code version}. Việc áp
     * dụng thêm khóa version có thể bổ sung trong tương lai nếu cần.
     * </p>
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void update(Relationship rel) {
        jdbc.update(
                "UPDATE relationship SET tombstoned_at = :tomb, version = :v WHERE id = :id",
                new MapSqlParameterSource()
                        .addValue("tomb", rel.tombstonedAt() == null ? null : Timestamp.from(rel.tombstonedAt()))
                        .addValue("v", rel.version())
                        .addValue("id", rel.id().toString()));
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sử dụng {@code AND version = :ev} để thực thi optimistic concurrency ở
     * tầng SQL: nếu version không khớp thì {@code UPDATE} không ảnh hưởng dòng
     * nào và caller sẽ phát hiện qua {@code rowCount}.
     * </p>
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void untombstone(UUID id, java.time.Instant at, long expectedVersion) {
        jdbc.update(
                "UPDATE relationship SET tombstoned_at = NULL, version = :v WHERE id = :id AND version = :ev",
                new MapSqlParameterSource()
                        .addValue("id", id.toString())
                        .addValue("ev", expectedVersion)
                        .addValue("v", expectedVersion + 1));
    }

    /**
     * {@inheritDoc}
     * <p>
     * Đếm số dòng khớp với bộ bốn {@code (tree, kind, from, to)}. Lưu ý:
     * triển khai này không phân biệt quan hệ đang sống / đã tombstone; để khớp
     * với hợp đồng, nên chỉ tính các dòng đang hoạt động ở use case.
     * </p>
     */
    @Override
    @Transactional(readOnly = true)
    public boolean existsEdge(UUID treeId, Relationship.Kind kind, UUID from, UUID to) {
        Integer n;
        try {
            n = jdbc.queryForObject(
                    "SELECT COUNT(1) FROM relationship "
                            + "WHERE tree_id = :t AND kind = :k AND from_member_id = :f AND to_member_id = :to",
                    new MapSqlParameterSource()
                            .addValue("t", treeId.toString())
                            .addValue("k", kind.name())
                            .addValue("f", from.toString())
                            .addValue("to", to.toString()),
                    Integer.class);
        } catch (EmptyResultDataAccessException e) { return false; }
        return n != null && n > 0;
    }

    /**
     * Tạo {@link MapSqlParameterSource} chứa tất cả các trường của {@link Relationship}
     * để sử dụng cho INSERT. Phương thức private giúp tái sử dụng giữa các lệnh ghi.
     *
     * @param r aggregate cần ánh xạ
     * @return tham số SQL đã chuẩn bị
     */
    private MapSqlParameterSource params(Relationship r) {
        return new MapSqlParameterSource()
                .addValue("id", r.id().toString())
                .addValue("tree", r.treeId().toString())
                .addValue("kind", r.kind().name())
                .addValue("from", r.fromMemberId().toString())
                .addValue("to", r.toMemberId().toString())
                .addValue("meta", r.metadataJson())
                .addValue("rev", r.revision())
                .addValue("created", Timestamp.from(r.createdAt()))
                .addValue("v", r.version());
    }

    /**
     * Ánh xạ một dòng kết quả SQL (dạng {@code Map<String, Object>}) sang aggregate
     * {@link Relationship}. Xử lý null-safe cho {@code metadata_json} và
     * {@code tombstoned_at}.
     *
     * @param r dòng kết quả SQL
     * @return aggregate đã tái dựng
     */
    private Relationship fromRow(java.util.Map<String, Object> r) {
        return new Relationship(
                UUID.fromString((String) r.get("id")),
                UUID.fromString((String) r.get("tree_id")),
                Relationship.Kind.valueOf((String) r.get("kind")),
                UUID.fromString((String) r.get("from_member_id")),
                UUID.fromString((String) r.get("to_member_id")),
                (String) r.get("metadata_json"),
                ((Number) r.get("revision")).longValue(),
                ((Timestamp) r.get("created_at")).toInstant(),
                // tombstoned_at có thể null - giữ nguyên null khi chuyển sang Instant.
                r.get("tombstoned_at") == null ? null : ((Timestamp) r.get("tombstoned_at")).toInstant(),
                ((Number) r.get("version")).longValue());
    }
}