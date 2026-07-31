package com.familya.sharing.adapter.out.persistence;

import com.familya.sharing.application.port.out.ShareWatermarkRepository;
import com.familya.sharing.domain.model.ShareLink;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter JDBC hiện thực {@link ShareWatermarkRepository} &mdash; thao tác với
 * bảng {@code share_allowlisted_projection} lưu trữ các projection công khai
 * đã được lọc trắng kèm watermark.
 * <p>
 * Đây là một phiên bản đơn giản hơn so với {@code JdbcAllowlistedProjectionRepository}
 * trong package {@code adapter.out.projection}: chỉ tập trung vào watermark và
 * projection dạng phẳng, không phân tách theo từng domain (member, media, ...).
 */
@Component
public class JdbcAllowlistedProjectionRepository implements ShareWatermarkRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Khởi tạo adapter.
     *
     * @param j template JDBC chia sẻ.
     */
    public JdbcAllowlistedProjectionRepository(NamedParameterJdbcTemplate j) {
        this.jdbc = j;
    }

    /**
     * Đọc watermark tối đa của một cây (tính trên tất cả các scope).
     *
     * @param t định danh cây gia phả.
     * @return giá trị watermark tối đa, hoặc {@code 0} nếu chưa có bản ghi.
     */
    @Override
    public long watermark(UUID t) {
        var r = jdbc.queryForList(
                "SELECT MAX(watermark) w FROM share_allowlisted_projection WHERE tree_id = :t",
                Map.of("t", t.toString()));
        // MAX trả về null khi không có dòng nào &mdash; fallback về 0.
        return r.isEmpty() || r.get(0).get("w") == null
                ? 0L : ((Number) r.get(0).get("w")).longValue();
    }

    /**
     * Cập nhật watermark &mdash; hiện chưa được hiện thực (no-op).
     * <p>
     * Phương thức tồn tại để tương thích với {@link ShareWatermarkRepository};
     * việc cập nhật watermark chính được thực hiện bởi
     * {@code JdbcAllowlistedProjectionRepository} (trong package
     * {@code adapter.out.projection}) thông qua {@code advanceWatermark(...)}.
     *
     * @param t        định danh cây gia phả.
     * @param w        giá trị watermark mới.
     * @param v        phiên bản aggregate.
     */
    @Override
    public void update(UUID t, long w, long v) {
        // No-op: watermark được cập nhật bởi adapter ở package projection.
    }

    /**
     * Đọc projection công khai cho một phạm vi/mục tiêu cụ thể.
     * <p>
     * Sử dụng toán tử {@code <=>} để so sánh {@code target_id} với {@code NULL}
     * một cách an toàn. Nếu parse JSON thất bại, ném {@link IllegalStateException}.
     *
     * @param t  định danh cây gia phả.
     * @param s  phạm vi chia sẻ.
     * @param id định danh mục tiêu (có thể {@code null} với {@code TREE}).
     * @return {@link Optional} chứa {@code Map<String,Object>} projection.
     */
    @Override
    public Optional<Map<String, Object>> projection(UUID t, ShareLink.Scope s, UUID id) {
        var r = jdbc.queryForList(
                "SELECT allowlisted_json FROM share_allowlisted_projection "
                        + "WHERE tree_id = :t AND scope = :s AND target_id <=> :id",
                new MapSqlParameterSource()
                        .addValue("t", t.toString())
                        .addValue("s", s.name())
                        .addValue("id", id == null ? null : id.toString()));
        if (r.isEmpty()) return Optional.empty();
        try {
            return Optional.of(mapper.readValue((String) r.get(0).get("allowlisted_json"),
                    new TypeReference<>() {}));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Lưu một projection kèm watermark (upsert). Dữ liệu projection được
     * serialize sang JSON bằng Jackson.
     *
     * @param t     định danh cây gia phả.
     * @param s     phạm vi chia sẻ.
     * @param id    định danh mục tiêu (rỗng khi {@code null} để phù hợp với cột).
     * @param value {@code Map} các trường projection.
     * @param w     phiên bản watermark.
     */
    @Override
    public void saveProjection(UUID t, ShareLink.Scope s, UUID id, Map<String, Object> value, long w) {
        try {
            jdbc.update(
                    "INSERT INTO share_allowlisted_projection (tree_id, scope, target_id, allowlisted_json, watermark, last_updated) "
                            + "VALUES (:t, :s, :id, :j, :w, :u) "
                            + "ON DUPLICATE KEY UPDATE allowlisted_json = :j, watermark = :w, last_updated = :u",
                    new MapSqlParameterSource()
                            .addValue("t", t.toString())
                            .addValue("s", s.name())
                            // Lưu ý: target_id dùng chuỗi rỗng thay cho null để khớp với ràng buộc khóa chính.
                            .addValue("id", id == null ? "" : id.toString())
                            .addValue("j", mapper.writeValueAsString(value))
                            .addValue("w", w)
                            .addValue("u", Timestamp.from(Instant.now())));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}