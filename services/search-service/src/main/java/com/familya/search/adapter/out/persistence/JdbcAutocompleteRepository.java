package com.familya.search.adapter.out.persistence;

import com.familya.search.application.port.out.AutocompleteRepository;
import com.familya.search.domain.model.AutocompleteEntry;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Triển khai JDBC của {@link AutocompleteRepository}, đọc từ bảng
 * {@code autocomplete_entry}.
 */
@Component
public class JdbcAutocompleteRepository implements AutocompleteRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Khởi tạo repository với JDBC template dùng chung.
     *
     * @param jdbc JDBC template dùng để truy vấn/xoá.
     */
    public JdbcAutocompleteRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Xoá toàn bộ mục autocomplete thuộc một cây.
     *
     * @param treeId định danh cây gia phả.
     * @return số bản ghi đã xoá.
     */
    @Override
    public long deleteByTree(UUID treeId) {
        return jdbc.update(
                "DELETE FROM autocomplete_entry WHERE tree_id = :t",
                new MapSqlParameterSource("t", treeId.toString()));
    }

    /**
     * Lấy danh sách gợi ý khớp tiền tố trong phạm vi cây, sắp xếp theo
     * trọng số giảm dần.
     *
     * @param normalizedPrefix tiền tố đã chuẩn hoá.
     * @param treeId           định danh cây gia phả.
     * @param limit            số kết quả tối đa.
     * @return danh sách {@link AutocompleteEntry}.
     */
    @Override
    public List<AutocompleteEntry> suggestions(String normalizedPrefix, UUID treeId, int limit) {
        // Dùng LIKE 'prefix%' để tận dụng chỉ mục cột normalized_prefix.
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
