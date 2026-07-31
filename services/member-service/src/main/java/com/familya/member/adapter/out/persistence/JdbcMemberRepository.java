package com.familya.member.adapter.out.persistence;

import com.familya.member.application.port.out.MemberRepository;
import com.familya.member.domain.model.CanonicalKey;
import com.familya.member.domain.model.Member;
import com.familya.member.domain.model.MemberAuthRow;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JdbcMemberRepository implements MemberRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcMemberRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(Member m) {
        jdbc.update(
                "INSERT INTO member (id, tree_id, user_id, display_name, given_name, surname, "
                        + "birth_date, death_date, birth_year_known, death_year_known, gender, status, "
                        + "generation, legacy_avatar_url, notes, created_at, updated_at, version) "
                        + "VALUES (:id, :tree, :user, :display, :given, :surname, "
                        + ":birth, :death, :bknown, :dknown, :gender, :status, "
                        + ":gen, :avatar, :notes, :created, :updated, :v)",
                params(m));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Member> findById(UUID id) {
        var rows = jdbc.queryForList(
                "SELECT id, tree_id, user_id, display_name, given_name, surname, "
                        + "birth_date, death_date, birth_year_known, death_year_known, gender, status, "
                        + "generation, legacy_avatar_url, notes, created_at, updated_at, tombstoned_at, version "
                        + "FROM member WHERE id = :id",
                new MapSqlParameterSource("id", id.toString()));
        return rows.isEmpty() ? Optional.empty() : Optional.of(fromRow(rows.get(0)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Member> listByTree(UUID treeId, boolean includeTombstoned) {
        String sql = includeTombstoned
                ? "SELECT * FROM member WHERE tree_id = :t ORDER BY created_at"
                : "SELECT * FROM member WHERE tree_id = :t AND tombstoned_at IS NULL ORDER BY created_at";
        var rows = jdbc.queryForList(sql, new MapSqlParameterSource("t", treeId.toString()));
        return rows.stream().map(this::fromRow).toList();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void update(Member m) {
        jdbc.update(
                "UPDATE member SET display_name = :display, given_name = :given, surname = :surname, "
                        + "birth_date = :birth, death_date = :death, birth_year_known = :bknown, "
                        + "death_year_known = :dknown, gender = :gender, status = :status, "
                        + "generation = :gen, legacy_avatar_url = :avatar, notes = :notes, "
                        + "updated_at = :updated, tombstoned_at = :tomb, version = :v WHERE id = :id",
                params(m));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findByCanonicalKey(CanonicalKey k) {
        var rows = jdbc.queryForList(
                "SELECT member_id FROM member_canonical_key "
                        + "WHERE tree_id = :t AND given_name = :g AND surname = :s "
                        + "AND ((:b IS NULL AND birth_date IS NULL) OR birth_date = :b)",
                new MapSqlParameterSource()
                        .addValue("t", k.treeId().toString())
                        .addValue("g", k.givenName())
                        .addValue("s", k.surname())
                        .addValue("b", k.birthDate() == null ? null : Date.valueOf(k.birthDate())));
        return rows.isEmpty() ? Optional.empty() : Optional.of(UUID.fromString((String) rows.get(0).get("member_id")));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void insertCanonicalKey(CanonicalKey k, UUID memberId) {
        jdbc.update(
                "INSERT INTO member_canonical_key (tree_id, given_name, surname, birth_date, member_id) "
                        + "VALUES (:t, :g, :s, :b, :m)",
                new MapSqlParameterSource()
                        .addValue("t", k.treeId().toString())
                        .addValue("g", k.givenName())
                        .addValue("s", k.surname())
                        .addValue("b", k.birthDate() == null ? null : Date.valueOf(k.birthDate()))
                        .addValue("m", memberId.toString()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void removeCanonicalKey(UUID memberId, CanonicalKey k) {
        jdbc.update(
                "DELETE FROM member_canonical_key WHERE tree_id = :t AND given_name = :g AND surname = :s "
                        + "AND ((:b IS NULL AND birth_date IS NULL) OR birth_date = :b) AND member_id = :m",
                new MapSqlParameterSource()
                        .addValue("t", k.treeId().toString())
                        .addValue("g", k.givenName())
                        .addValue("s", k.surname())
                        .addValue("b", k.birthDate() == null ? null : Date.valueOf(k.birthDate()))
                        .addValue("m", memberId.toString()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MemberAuthRow> findAuth(UUID treeId, UUID userId) {
        var rows = jdbc.queryForList(
                "SELECT tree_id, user_id, role, revision, epoch, granted_at, revoked, source_event_id, last_updated_at "
                        + "FROM authorization_projection WHERE tree_id = :t AND user_id = :u",
                new MapSqlParameterSource().addValue("t", treeId.toString()).addValue("u", userId.toString()));
        if (rows.isEmpty()) return Optional.empty();
        return Optional.of(authFromRow(rows.get(0)));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void upsertAuth(MemberAuthRow r) {
        jdbc.update(
                "INSERT INTO authorization_projection "
                        + "(tree_id, user_id, role, revision, epoch, granted_at, revoked, source_event_id, last_updated_at) "
                        + "VALUES (:t, :u, :role, :rev, :epoch, :at, :revoked, :src, :upd) "
                        + "ON DUPLICATE KEY UPDATE role = VALUES(role), revision = VALUES(revision), "
                        + "epoch = VALUES(epoch), revoked = VALUES(revoked), "
                        + "source_event_id = VALUES(source_event_id), last_updated_at = VALUES(last_updated_at)",
                authParams(r));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void updateAuthRole(UUID treeId, UUID userId, String role, boolean revoked,
                                long revision, long epoch, java.time.Instant grantedAt,
                                String sourceEventId, java.time.Instant now) {
        jdbc.update(
                "INSERT INTO authorization_projection "
                        + "(tree_id, user_id, role, revision, epoch, granted_at, revoked, source_event_id, last_updated_at) "
                        + "VALUES (:t, :u, :role, :rev, :epoch, :at, :revoked, :src, :upd) "
                        + "ON DUPLICATE KEY UPDATE role = VALUES(role), revision = VALUES(revision), "
                        + "epoch = VALUES(epoch), revoked = VALUES(revoked), "
                        + "source_event_id = VALUES(source_event_id), last_updated_at = VALUES(last_updated_at)",
                new MapSqlParameterSource()
                        .addValue("t", treeId.toString())
                        .addValue("u", userId.toString())
                        .addValue("role", role)
                        .addValue("rev", revision)
                        .addValue("epoch", epoch)
                        .addValue("at", Timestamp.from(grantedAt))
                        .addValue("revoked", revoked)
                        .addValue("src", sourceEventId)
                        .addValue("upd", Timestamp.from(now)));
    }

    private MapSqlParameterSource params(Member m) {
        return new MapSqlParameterSource()
                .addValue("id", m.id().toString())
                .addValue("tree", m.treeId().toString())
                .addValue("user", m.userId() == null ? null : m.userId().toString())
                .addValue("display", m.displayName())
                .addValue("given", m.givenName())
                .addValue("surname", m.surname())
                .addValue("birth", m.birthDate() == null ? null : Date.valueOf(m.birthDate()))
                .addValue("death", m.deathDate() == null ? null : Date.valueOf(m.deathDate()))
                .addValue("bknown", m.birthYearKnown())
                .addValue("dknown", m.deathYearKnown())
                .addValue("gender", m.gender() == null ? null : m.gender().name())
                .addValue("status", m.status().name())
                .addValue("gen", m.generation())
                .addValue("avatar", m.legacyAvatarUrl())
                .addValue("notes", m.notes())
                .addValue("created", Timestamp.from(m.createdAt()))
                .addValue("updated", Timestamp.from(m.updatedAt()))
                .addValue("tomb", m.tombstonedAt() == null ? null : Timestamp.from(m.tombstonedAt()))
                .addValue("v", m.version());
    }

    private MapSqlParameterSource authParams(MemberAuthRow r) {
        return new MapSqlParameterSource()
                .addValue("t", r.treeId().toString())
                .addValue("u", r.userId().toString())
                .addValue("role", r.role())
                .addValue("rev", r.revision())
                .addValue("epoch", r.epoch())
                .addValue("at", Timestamp.from(r.grantedAt()))
                .addValue("revoked", r.revoked())
                .addValue("src", r.sourceEventId())
                .addValue("upd", Timestamp.from(r.lastUpdatedAt()));
    }

    private Member fromRow(java.util.Map<String, Object> r) {
        LocalDate birth = r.get("birth_date") == null ? null : ((Date) r.get("birth_date")).toLocalDate();
        LocalDate death = r.get("death_date") == null ? null : ((Date) r.get("death_date")).toLocalDate();
        return new Member(
                UUID.fromString((String) r.get("id")),
                UUID.fromString((String) r.get("tree_id")),
                r.get("user_id") == null ? null : UUID.fromString((String) r.get("user_id")),
                (String) r.get("display_name"),
                (String) r.get("given_name"),
                (String) r.get("surname"),
                birth, death,
                Boolean.TRUE.equals(r.get("birth_year_known")),
                Boolean.TRUE.equals(r.get("death_year_known")),
                r.get("gender") == null ? null : Member.Gender.valueOf((String) r.get("gender")),
                Member.Status.valueOf((String) r.get("status")),
                r.get("generation") == null ? null : ((Number) r.get("generation")).intValue(),
                (String) r.get("legacy_avatar_url"),
                (String) r.get("notes"),
                ((Timestamp) r.get("created_at")).toInstant(),
                ((Timestamp) r.get("updated_at")).toInstant(),
                r.get("tombstoned_at") == null ? null : ((Timestamp) r.get("tombstoned_at")).toInstant(),
                ((Number) r.get("version")).longValue());
    }

    private MemberAuthRow authFromRow(java.util.Map<String, Object> r) {
        return new MemberAuthRow(
                UUID.fromString((String) r.get("tree_id")),
                UUID.fromString((String) r.get("user_id")),
                (String) r.get("role"),
                ((Number) r.get("revision")).longValue(),
                ((Number) r.get("epoch")).longValue(),
                ((Timestamp) r.get("granted_at")).toInstant(),
                Boolean.TRUE.equals(r.get("revoked")),
                (String) r.get("source_event_id"),
                ((Timestamp) r.get("last_updated_at")).toInstant());
    }
}