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

@Component
public class JdbcIdentityRepository implements IdentityRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcIdentityRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

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

    @Override
    public Optional<User> findById(UUID id) {
        var rows = jdbc.query("SELECT * FROM users WHERE id = :id",
                new MapSqlParameterSource("id", id.toString()), USER_ROW);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public Optional<User> findByNormalizedEmail(String normalizedEmail) {
        var rows = jdbc.query("SELECT * FROM users WHERE normalized_email = :e",
                new MapSqlParameterSource("e", normalizedEmail), USER_ROW);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

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
                        .addValue("cver", user.version() - 1));
    }

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
        return s.revoked() ? Optional.of(s.revoke()) : Optional.of(s);
    }

    @Override
    public void updateSession(Session s) {
        jdbc.update("UPDATE session SET revoked = :r WHERE id = :id",
                new MapSqlParameterSource("id", s.id().toString()).addValue("r", s.revoked()));
    }

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

    @Override
    public void consumeEmailVerificationToken(String token, Instant consumedAt) {
        jdbc.update(
                "UPDATE email_verification_token SET consumed_at = :ca WHERE token = :t",
                new MapSqlParameterSource()
                        .addValue("t", token)
                        .addValue("ca", Timestamp.from(consumedAt)));
    }

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
