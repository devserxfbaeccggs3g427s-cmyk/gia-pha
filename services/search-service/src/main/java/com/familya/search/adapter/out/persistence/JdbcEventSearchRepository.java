package com.familya.search.adapter.out.persistence;

import com.familya.search.application.port.in.SearchEventsCommand;
import com.familya.search.application.port.out.EventSearchRepository;
import com.familya.search.domain.model.EventSearchDocument;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/**
 * Triển khai JDBC của {@link EventSearchRepository}, đọc từ bảng
 * {@code search_event_doc}.
 */
@Component
public class JdbcEventSearchRepository implements EventSearchRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Khởi tạo repository với JDBC template dùng chung.
     *
     * @param jdbc JDBC template dùng để truy vấn/xoá.
     */
    public JdbcEventSearchRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Xoá toàn bộ tài liệu sự kiện thuộc một cây.
     *
     * @param treeId định danh cây gia phả.
     * @return số bản ghi đã xoá.
     */
    @Override
    public long deleteByTree(UUID treeId) {
        return jdbc.update(
                "DELETE FROM search_event_doc WHERE tree_id = :t",
                new MapSqlParameterSource("t", treeId.toString()));
    }

    /**
     * Tìm kiếm tài liệu sự kiện theo chuỗi đã chuẩn hoá và bộ lọc.
     *
     * <p>SQL được dựng động: các mệnh đề WHERE chỉ được thêm vào khi trường
     * tương ứng của {@code filter} (hoặc {@code normalizedQuery}) khác
     * {@code null}. Điều này giúp giữ chỉ mục được sử dụng tối đa.</p>
     *
     * @param filter          bộ lọc (kind, khoảng ngày, tombstoned) hoặc {@code null}.
     * @param normalizedQuery chuỗi truy vấn đã chuẩn hoá; rỗng/blank thì bỏ qua LIKE.
     * @param treeId          định danh cây gia phả.
     * @param limit           số kết quả tối đa.
     * @return danh sách tài liệu khớp.
     */
    @Override
    public List<EventSearchDocument> search(SearchEventsCommand.EventFilter filter,
                                             String normalizedQuery, UUID treeId, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT tree_id, event_id, title, start_date, kind, tombstoned, last_updated "
                        + "FROM search_event_doc WHERE tree_id = :t");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("t", treeId.toString())
                .addValue("limit", limit);
        if (normalizedQuery != null && !normalizedQuery.isBlank()) {
            sql.append(" AND normalized_title LIKE :q");
            // Bao chuỗi bằng % để khớp "chứa" thay vì "bắt đầu bằng".
            params.addValue("q", "%" + normalizedQuery + "%");
        }
        if (filter != null) {
            if (filter.kind() != null) {
                sql.append(" AND kind = :k");
                params.addValue("k", filter.kind());
            }
            if (filter.from() != null) {
                sql.append(" AND start_date >= :from");
                params.addValue("from", Date.valueOf(filter.from()));
            }
            if (filter.to() != null) {
                sql.append(" AND start_date <= :to");
                params.addValue("to", Date.valueOf(filter.to()));
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
     * Ánh xạ một dòng kết quả thành {@link EventSearchDocument}.
     *
     * @param r dòng kết quả từ JDBC.
     * @return tài liệu sự kiện tương ứng.
     */
    private EventSearchDocument fromRow(java.util.Map<String, Object> r) {
        return new EventSearchDocument(
                UUID.fromString((String) r.get("tree_id")),
                UUID.fromString((String) r.get("event_id")),
                (String) r.get("title"),
                // start_date có thể null - giữ null thay vì ném ngoại lệ.
                r.get("start_date") == null ? null : ((Date) r.get("start_date")).toLocalDate(),
                (String) r.get("kind"),
                Boolean.TRUE.equals(r.get("tombstoned")),
                ((Timestamp) r.get("last_updated")).toInstant());
    }
}
