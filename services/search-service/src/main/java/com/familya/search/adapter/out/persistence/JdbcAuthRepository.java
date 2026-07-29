package com.familya.search.adapter.out.persistence;

import com.familya.search.application.port.out.SearchAuthRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

@Component
public class JdbcAuthRepository implements SearchAuthRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcAuthRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<SearchAuthRepository.AuthRow> find(UUID treeId, UUID userId) {
        var rows = jdbc.queryForList(
                "SELECT tree_id, user_id, role, revision, revoked, last_updated_at "
                        + "FROM authorization_projection WHERE tree_id = :t AND user_id = :u",
                new MapSqlParameterSource()
                        .addValue("t", treeId.toString())
                        .addValue("u", userId.toString()));
        if (rows.isEmpty()) return Optional.empty();
        var r = rows.get(0);
        return Optional.of(new SearchAuthRepository.AuthRow(
                UUID.fromString((String) r.get("tree_id")),
                UUID.fromString((String) r.get("user_id")),
                (String) r.get("role"),
                ((Number) r.get("revision")).longValue(),
                Boolean.TRUE.equals(r.get("revoked")),
                ((Timestamp) r.get("last_updated_at")).toInstant()));
    }
}
