package com.familya.search.adapter.out.persistence;

import com.familya.search.application.port.in.SearchMediaCommand;
import com.familya.search.application.port.out.MediaSearchRepository;
import com.familya.search.domain.model.MediaSearchDocument;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/**
 * Triển khai JDBC của {@link MediaSearchRepository}, đọc từ bảng
 * {@code search_media_doc}.
 */
@Component
public class JdbcMediaSearchRepository implements MediaSearchRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Khởi tạo repository với JDBC template dùng chung.
     *
     * @param jdbc JDBC template dùng để truy vấn/xoá.
     */
    public JdbcMediaSearchRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Xoá toàn bộ tài liệu media thuộc một cây.
     *
     * @param treeId định danh cây gia phả.
     * @return số bản ghi đã xoá.
     */
    @Override
    public long deleteByTree(UUID treeId) {
        return jdbc.update(
                "DELETE FROM search_media_doc WHERE tree_id = :t",
                new MapSqlParameterSource("t", treeId.toString()));
    }

    /**
     * Tìm kiếm tài liệu media theo chuỗi đã chuẩn hoá và bộ lọc.
     *
     * @param filter          bộ lọc (kind, tombstoned) hoặc {@code null}.
     * @param normalizedQuery chuỗi truy vấn đã chuẩn hoá; rỗng/blank thì bỏ qua LIKE.
     * @param treeId          định danh cây gia phả.
     * @param limit           số kết quả tối đa.
     * @return danh sách tài liệu khớp.
     */
    @Override
    public List<MediaSearchDocument> search(SearchMediaCommand.MediaFilter filter,
                                            String normalizedQuery, UUID treeId, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT tree_id, media_id, filename, kind, tombstoned, last_updated "
                        + "FROM search_media_doc WHERE tree_id = :t");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("t", treeId.toString())
                .addValue("limit", limit);
        if (normalizedQuery != null && !normalizedQuery.isBlank()) {
            sql.append(" AND normalized_filename LIKE :q");
            params.addValue("q", "%" + normalizedQuery + "%");
        }
        if (filter != null) {
            if (filter.kind() != null) {
                sql.append(" AND kind = :k");
                params.addValue("k", filter.kind());
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

    /**
     * Ánh xạ một dòng kết quả thành {@link MediaSearchDocument}.
     *
     * @param r dòng kết quả từ JDBC.
     * @return tài liệu media tương ứng.
     */
    private MediaSearchDocument fromRow(java.util.Map<String, Object> r) {
        return new MediaSearchDocument(
                UUID.fromString((String) r.get("tree_id")),
                UUID.fromString((String) r.get("media_id")),
                (String) r.get("filename"),
                (String) r.get("kind"),
                Boolean.TRUE.equals(r.get("tombstoned")),
                ((Timestamp) r.get("last_updated")).toInstant());
    }
}
