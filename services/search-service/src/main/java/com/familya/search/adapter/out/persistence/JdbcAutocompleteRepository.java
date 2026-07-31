package com.familya.search.adapter.out.persistence;

import com.familya.search.application.port.out.AutocompleteRepository;
import com.familya.search.domain.model.AutocompleteEntry;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class JdbcAutocompleteRepository implements AutocompleteRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcAutocompleteRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long deleteByTree(UUID treeId) {
        return jdbc.update(
                "DELETE FROM autocomplete_entry WHERE tree_id = :t",
                new MapSqlParameterSource("t", treeId.toString()));
    }

    @Override
    public List<AutocompleteEntry> suggestions(String normalizedPrefix, UUID treeId, int limit) {
        var rows = jdbc.queryForList(
                "SELECT tree_id, owner_id, surface, normalized_prefix, weight "
                        + "FROM autocomplete_entry WHERE tree_id = :t AND normalized_prefix LIKE :p "
                        + "ORDER BY weight DESC LIMIT :limit",
                new MapSqlParameterSource()
                        .addValue("t", treeId.toString())
                        .addValue("p", normalizedPrefix + "%")
                        .addValue("limit", limit));
        return rows.stream().map(r -> new AutocompleteEntry(
                UUID.fromString((String) r.get("tree_id")),
                r.get("owner_id") == null ? null : UUID.fromString((String) r.get("owner_id")),
                (String) r.get("surface"),
                (String) r.get("normalized_prefix"),
                ((Number) r.get("weight")).intValue())).toList();
    }
}
