package com.familya.search.adapter.out.persistence;

import com.familya.search.application.port.in.SearchMembersCommand;
import com.familya.search.application.port.out.MemberSearchRepository;
import com.familya.search.domain.model.MemberSearchDocument;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Component
public class JdbcMemberSearchRepository implements MemberSearchRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcMemberSearchRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long deleteByTree(UUID treeId) {
        int rows = jdbc.update(
                "DELETE FROM search_member_doc WHERE tree_id = :t",
                new MapSqlParameterSource("t", treeId.toString()));
        return rows;
    }

    @Override
    public List<MemberSearchDocument> search(SearchMembersCommand.MemberFilter filter,
                                              String normalizedQuery, UUID treeId, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT tree_id, member_id, full_name, given_name, surname, birth_year, death_year, "
                        + "tombstoned, last_updated FROM search_member_doc WHERE tree_id = :t");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("t", treeId.toString())
                .addValue("limit", limit);
        if (normalizedQuery != null && !normalizedQuery.isBlank()) {
            sql.append(" AND normalized_name LIKE :q");
            params.addValue("q", "%" + normalizedQuery + "%");
        }
        if (filter != null) {
            if (filter.birthYearFrom() != null) {
                sql.append(" AND birth_year >= :byFrom");
                params.addValue("byFrom", filter.birthYearFrom());
            }
            if (filter.birthYearTo() != null) {
                sql.append(" AND birth_year <= :byTo");
                params.addValue("byTo", filter.birthYearTo());
            }
            if (filter.birthYear() != null) {
                sql.append(" AND birth_year = :by");
                params.addValue("by", filter.birthYear());
            }
            if (filter.tombstoned() != null) {
                sql.append(" AND tombstoned = :tomb");
                params.addValue("tomb", filter.tombstoned());
            }
        }
        sql.append(" ORDER BY last_updated DESC LIMIT :limit");
        var rows = jdbc.queryForList(sql.toString(), params);
        return rows.stream().map(this::fromRow).toList();
    }

    private MemberSearchDocument fromRow(java.util.Map<String, Object> r) {
        return new MemberSearchDocument(
                UUID.fromString((String) r.get("tree_id")),
                UUID.fromString((String) r.get("member_id")),
                (String) r.get("full_name"),
                (String) r.get("given_name"),
                (String) r.get("surname"),
                r.get("birth_year") == null ? null : ((Number) r.get("birth_year")).intValue(),
                r.get("death_year") == null ? null : ((Number) r.get("death_year")).intValue(),
                Boolean.TRUE.equals(r.get("tombstoned")),
                ((Timestamp) r.get("last_updated")).toInstant());
    }
}
