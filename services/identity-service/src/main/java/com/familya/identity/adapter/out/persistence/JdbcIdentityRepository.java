package com.familya.identity.adapter.out.persistence;

import com.familya.identity.application.port.out.IdentityRepository;
import com.familya.identity.domain.model.OAuthLink;
import com.familya.identity.domain.model.Session;
import com.familya.identity.domain.model.User;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter persistence triển khai {@link IdentityRepository} bằng JDBC
 * (sử dụng {@link NamedParameterJdbcTemplate}).
 *
 * <p>Triển khai này là một phần của tầng "adapters out" trong kiến trúc
 * hexagonal, là cầu nối duy nhất giữa tầng application và cơ sở dữ liệu
 * quan hệ của {@code identity-service}. Mọi thao tác đọc/ghi đều đi qua
 * đây nhằm đảm bảo:
 * <ul>
 *     <li>Dễ dàng viết unit test với H2 / Testcontainers.</li>
 *     <li>Tách biệt SQL khỏi logic nghiệp vụ – có thể thay thế bằng
 *         triển khai khác (JPA, R2DBC,…) mà không ảnh hưởng use case.</li>
 *     <li>Tối ưu hóa tường minh: việc sử dụng {@code version} trong
 *         UPDATE giúp thực hiện "optimistic locking" thủ công.</li>
 * </ul>
 *
 * @author Familya Platform Team
 * @since 1.0.0
 */
@Component
public class JdbcIdentityRepository implements IdentityRepository {

    /** Template JDBC hỗ trợ tham số đặt tên. */
    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Khởi tạo adapter với {@link NamedParameterJdbcTemplate} được
     * Spring Boot cấu hình sẵn.
     *
     * @param jdbc template JDBC dùng chung.
     */
    public JdbcIdentityRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * {@link RowMapper} ánh xạ một dòng trong bảng {@code users} sang
     * aggregate {@link User}.
     *
     * <p>Ánh xạ thực hiện:
     * <ul>
     *     <li>Chuyển {@code id} từ chuỗi sang {@link UUID}.</li>
     *     <li>Chuyển các cột {@code locked}, {@code locked_until},
     *         {@code failed_attempts} thành {@link User.LockoutState}
     *         kết hợp với số lần đăng nhập thất bại.</li>
     *     <li>Chuyển các cột timestamp thành {@link Instant}.</li>
     *     <li>Chuyển {@code verification_state} thành enum
     *         {@link User.VerificationState}.</li>
     * </ul>
     */
    private static final RowMapper<User> USER_ROW = (rs, n) -> new User(
            UUID.fromString(rs.getString("id")),
            rs.getString("normalized_email"),
            rs.getString("bcrypt_hash"),
            User.VerificationState.valueOf(rs.getString("verification_state")),
            new User.LockoutState(rs.getBoolean("locked"),
                    rs.getTimestamp("locked_until") == null ? null : rs.getTimestamp("locked_until").toInstant()),
            rs.getInt("failed_attempts"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getLong("version"));

    /**
     * Tìm người dùng theo {@code id}.
     *
     * <p>Thực hiện truy vấn {@code SELECT * FROM users WHERE id = :id}
     * và trả về {@link Optional} rỗng nếu không tìm thấy. Sử dụng
     * {@link Optional#of} chỉ khi có đúng một bản ghi (mặc dù id là
     * khóa chính nên kết quả luôn là 0 hoặc 1).
     *
     * @param id UUID người dùng cần tìm.
     * @return {@link Optional} chứa {@link User} nếu tồn tại, ngược lại
     *         trả về {@link Optional#empty()}.
     */
    @Override
    public Optional<User> findById(UUID id) {
        var rows = jdbc.query("SELECT * FROM users WHERE id = :id",
                new MapSqlParameterSource("id", id.toString()), USER_ROW);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * Tìm người dùng theo email đã được chuẩn hóa (lowercase, trim).
     *
     * @param normalizedEmail email chuẩn hóa.
     * @return {@link Optional} chứa {@link User} nếu tồn tại, ngược lại
     *         trả về {@link Optional#empty()}.
     */
    @Override
    public Optional<User> findByNormalizedEmail(String normalizedEmail) {
        var rows = jdbc.query("SELECT * FROM users WHERE normalized_email = :e",
                new MapSqlParameterSource("e", normalizedEmail), USER_ROW);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * Thêm mới một {@link User} vào cơ sở dữ liệu.
     *
     * <p>Tham số {@code oAuthLinks} hiện chưa được sử dụng trong câu
     * INSERT này – đây là điểm mở rộng cho việc chèn liên kết OAuth
     * trong cùng transaction (sẽ được bổ sung ở phiên bản tiếp theo).
     *
     * @param user        aggregate người dùng cần chèn.
     * @param oAuthLinks  danh sách liên kết OAuth (hiện chưa dùng).
     */
    @Override
    public void insert(User user, List<OAuthLink> oAuthLinks) {
        jdbc.update(
                "INSERT INTO users (id, normalized_email, bcrypt_hash, verification_state, "
                        + "locked, locked_until, failed_attempts, created_at, version) "
                        + "VALUES (:id, :e, :b, :v, :l, :lu, :fa, :ca, :ve)",
                new MapSqlParameterSource()
                        .addValue("id", user.id().toString())
                        .addValue("e", user.normalizedEmail())
                        .addValue("b", user.bcryptHash())
                        .addValue("v", user.verification().name())
                        .addValue("l", user.lockout().locked())
                        .addValue("lu", user.lockout().lockedUntil() == null ? null : Timestamp.from(user.lockout().lockedUntil()))
                        .addValue("fa", user.failedAttempts())
                        .addValue("ca", Timestamp.from(user.createdAt()))
                        .addValue("ve", user.version()));
    }

    /**
     * Cập nhật thông tin người dùng với optimistic locking.
     *
     * <p>Câu lệnh UPDATE yêu cầu {@code version = :cver} (phiên bản kỳ
     * vọng), trong khi giá trị mới được lưu là {@code version + 1}.
     * Nếu một transaction khác đã cập nhật bản ghi trước đó, số dòng
     * bị ảnh hưởng sẽ bằng 0 và ngoại lệ
     * {@link org.springframework.dao.OptimisticLockingFailureException}
     * sẽ được ném – đây là cơ chế phát hiện xung đột đồng thời.
     *
     * @param user aggregate người dùng cần cập nhật.
     */
    @Override
    public void update(User user) {
        jdbc.update(
                "UPDATE users SET bcrypt_hash = :b, verification_state = :v, locked = :l, "
                        + "locked_until = :lu, failed_attempts = :fa, version = :ve "
                        + "WHERE id = :id AND version = :cver",
                new MapSqlParameterSource()
                        .addValue("b", user.bcryptHash())
                        .addValue("v", user.verification().name())
                        .addValue("l", user.lockout().locked())
                        .addValue("lu", user.lockout().lockedUntil() == null ? null : Timestamp.from(user.lockout().lockedUntil()))
                        .addValue("fa", user.failedAttempts())
                        .addValue("ve", user.version())
                        .addValue("id", user.id().toString())
                        // cver là phiên bản hiện tại trong DB, kỳ vọng = version - 1.
                        .addValue("cver", user.version() - 1));
    }

    /**
     * Lưu một phiên đăng nhập mới.
     *
     * @param s phiên cần chèn.
     */
    @Override
    public void insertSession(Session s) {
        jdbc.update(
                "INSERT INTO session (id, user_id, created_at, absolute_expires_at, idle_expires_at, user_agent, ip_hash, revoked) "
                        + "VALUES (:id, :u, :ca, :ae, :ie, :ua, :ih, :r)",
                new MapSqlParameterSource()
                        .addValue("id", s.id().toString())
                        .addValue("u", s.userId().toString())
                        .addValue("ca", Timestamp.from(s.createdAt()))
                        .addValue("ae", Timestamp.from(s.absoluteExpiresAt()))
                        .addValue("ie", Timestamp.from(s.idleExpiresAt()))
                        .addValue("ua", s.userAgent())
                        .addValue("ih", s.ipHash())
                        .addValue("r", s.revoked()));
    }

    /**
     * Tìm phiên đăng nhập theo {@code id}.
     *
     * <p>Nếu phiên đã bị thu hồi, phương thức sẽ trả về một bản sao
     * với cờ {@code revoked = true} (gọi {@link Session#revoke()}).
     * Việc này đảm bảo bất kỳ tầng nào sử dụng kết quả đều nhận
     * được trạng thái thu hồi rõ ràng.
     *
     * @param id UUID phiên.
     * @return {@link Optional} chứa {@link Session} nếu tồn tại, ngược
     *         lại trả về {@link Optional#empty()}.
     */
    @Override
    public Optional<Session> findSession(UUID id) {
        var rows = jdbc.query(
                "SELECT * FROM session WHERE id = :id",
                new MapSqlParameterSource("id", id.toString()),
                (rs, n) -> new Session(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("user_id")),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("absolute_expires_at").toInstant(),
                        rs.getTimestamp("idle_expires_at").toInstant(),
                        rs.getString("user_agent"),
                        rs.getString("ip_hash")));
        if (rows.isEmpty()) return Optional.empty();
        Session s = rows.get(0);
        // Nếu DB ghi nhận đã thu hồi, đảm bảo đối tượng cũng phản ánh điều đó.
        return s.revoked() ? Optional.of(s.revoke()) : Optional.of(s);
    }

    /**
     * Cập nhật trạng thái thu hồi của phiên.
     *
     * @param s phiên với cờ {@code revoked} mới.
     */
    @Override
    public void updateSession(Session s) {
        jdbc.update("UPDATE session SET revoked = :r WHERE id = :id",
                new MapSqlParameterSource("id", s.id().toString()).addValue("r", s.revoked()));
    }

    /**
     * Lưu một liên kết OAuth (Google, Facebook,…) của người dùng.
     *
     * @param link liên kết OAuth cần chèn.
     */
    @Override
    public void insertOAuthLink(OAuthLink link) {
        jdbc.update(
                "INSERT INTO oauth_link (id, user_id, provider, provider_subject, normalized_email) "
                        + "VALUES (:id, :u, :p, :s, :e)",
                new MapSqlParameterSource()
                        .addValue("id", link.id().toString())
                        .addValue("u", link.userId().toString())
                        .addValue("p", link.provider())
                        .addValue("s", link.providerSubject())
                        .addValue("e", link.normalizedEmail()));
    }

    /**
     * Đánh dấu token xác minh email đã được sử dụng.
     *
     * <p>Cập nhật cột {@code consumed_at} để chống tái sử dụng token.
     *
     * @param token      mã token cần đánh dấu.
     * @param consumedAt thời điểm tiêu thụ (thường là {@code Instant.now()}).
     */
    @Override
    public void consumeEmailVerificationToken(String token, Instant consumedAt) {
        jdbc.update(
                "UPDATE email_verification_token SET consumed_at = :ca WHERE token = :t",
                new MapSqlParameterSource()
                        .addValue("t", token)
                        .addValue("ca", Timestamp.from(consumedAt)));
    }

    /**
     * Tìm token xác minh email theo chuỗi token.
     *
     * @param token chuỗi token cần tra cứu.
     * @return {@link Optional} chứa {@link com.familya.identity.domain.model.EmailVerificationToken}
     *         nếu tồn tại, ngược lại trả về {@link Optional#empty()}.
     */
    @Override
    public java.util.Optional<com.familya.identity.domain.model.EmailVerificationToken> findEmailVerificationToken(String token) {
        var rows = jdbc.query(
                "SELECT * FROM email_verification_token WHERE token = :t",
                new MapSqlParameterSource("t", token),
                (rs, n) -> new com.familya.identity.domain.model.EmailVerificationToken(
                        UUID.fromString(rs.getString("user_id")),
                        rs.getString("token"),
                        rs.getTimestamp("expires_at").toInstant(),
                        rs.getTimestamp("consumed_at") == null ? null : rs.getTimestamp("consumed_at").toInstant()));
        return rows.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(rows.get(0));
    }
}
