/**
 * Adapter JDBC cho port {@link com.familya.auditops.application.port.out.AuditAppender}.
 *
 * <p>Lớp này chịu trách nhiệm ghi và truy vấn bảng {@code audit_event}
 * trong cơ sở dữ liệu cục bộ. Mọi thao tác ghi đều chạy trong
 * transaction hiện tại (propagation = MANDATORY) để đảm bảo audit row
 * chỉ được tạo khi nghiệp vụ liên quan thành công.</p>
 */
package com.familya.auditops.adapter.out.persistence;

import com.familya.auditops.application.port.out.AuditAppender;
import com.familya.auditops.domain.model.AuditEvent;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Triển khai {@link AuditAppender} bằng JDBC.
 *
 * <p>Các thao tác đọc ({@code findByOperation}, {@code findById}) chạy
 * trong transaction chỉ-đọc để tối ưu hiệu năng và tránh khả năng
 * ghi nhầm.</p>
 */
@Component
public class JdbcAuditAppender implements AuditAppender {

    /** JDBC template dùng chung. */
    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Khởi tạo appender.
     *
     * @param jdbc JDBC template
     */
    public JdbcAuditAppender(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Thêm một sự kiện audit vào bảng {@code audit_event}.
     *
     * <p>Yêu cầu phải đang có transaction ({@link Propagation#MANDATORY}).
     * Tham số {@code detail} được serialize sang JSON.</p>
     *
     * @param e sự kiện audit cần ghi
     * @return sự kiện đã ghi (giữ nguyên tham chiếu)
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEvent append(AuditEvent e) {
        jdbc.update(
                "INSERT INTO audit_event (audit_id, operation_id, correlation_id, actor_user_id, actor_kind, "
                        + "action, target_type, target_id, detail_json, occurred_at, trace_id) "
                        + "VALUES (:id, :op, :co, :au, :ak, :action, :tt, :ti, :detail, :oa, :tr)",
                new MapSqlParameterSource()
                        .addValue("id", e.auditId().toString())
                        .addValue("op", e.operationId() == null ? null : e.operationId().toString())
                        .addValue("co", e.correlationId() == null ? null : e.correlationId().toString())
                        .addValue("au", e.actorUserId() == null ? null : e.actorUserId().toString())
                        .addValue("ak", e.actorKind().name())
                        .addValue("action", e.action())
                        .addValue("tt", e.targetType())
                        .addValue("ti", e.targetId())
                        .addValue("detail", json(e.detail()))
                        .addValue("oa", Timestamp.from(e.occurredAt()))
                        .addValue("tr", e.traceId()));
        return e;
    }

    /**
     * Tìm các sự kiện audit của một operation, sắp xếp theo thời gian
     * giảm dần và giới hạn bởi {@code limit}.
     *
     * @param operationId id operation
     * @param limit       số lượng tối đa
     * @return danh sách sự kiện audit
     */
    @Override
    @Transactional(readOnly = true)
    public List<AuditEvent> findByOperation(UUID operationId, int limit) {
        var rows = jdbc.queryForList(
                "SELECT audit_id, operation_id, correlation_id, actor_user_id, actor_kind, action, target_type, "
                        + "target_id, detail_json, occurred_at, trace_id FROM audit_event "
                        + "WHERE operation_id = :op ORDER BY occurred_at DESC LIMIT :l",
                new MapSqlParameterSource()
                        .addValue("op", operationId.toString())
                        .addValue("l", limit));
        return rows.stream().map(this::fromRow).toList();
    }

    /**
     * Tìm một sự kiện audit theo id.
     *
     * @param auditId id audit
     * @return {@link Optional} chứa sự kiện nếu tồn tại
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<AuditEvent> findById(UUID auditId) {
        var rows = jdbc.queryForList(
                "SELECT audit_id, operation_id, correlation_id, actor_user_id, actor_kind, action, target_type, "
                        + "target_id, detail_json, occurred_at, trace_id FROM audit_event WHERE audit_id = :id",
                new MapSqlParameterSource("id", auditId.toString()));
        if (rows.isEmpty()) return Optional.empty();
        return Optional.of(fromRow(rows.get(0)));
    }

    /**
     * Chuyển {@link java.util.Map} từ JDBC row sang {@link AuditEvent}.
     *
     * @param r map kết quả JDBC
     * @return sự kiện audit đã chuyển đổi
     */
    private AuditEvent fromRow(Map<String, Object> r) {
        return new AuditEvent(
                UUID.fromString((String) r.get("audit_id")),
                r.get("operation_id") == null ? null : UUID.fromString((String) r.get("operation_id")),
                r.get("correlation_id") == null ? null : UUID.fromString((String) r.get("correlation_id")),
                r.get("actor_user_id") == null ? null : UUID.fromString((String) r.get("actor_user_id")),
                AuditEvent.ActorKind.valueOf((String) r.get("actor_kind")),
                (String) r.get("action"),
                (String) r.get("target_type"),
                (String) r.get("target_id"),
                parseJson((String) r.get("detail_json")),
                ((Timestamp) r.get("occurred_at")).toInstant(),
                (String) r.get("trace_id"));
    }

    /**
     * Serialize {@link java.util.Map} sang chuỗi JSON.
     *
     * @param m map cần serialize
     * @return chuỗi JSON hoặc null nếu map rỗng
     * @throws IllegalStateException nếu serialize thất bại
     */
    private static String json(Map<String, Object> m) {
        if (m == null || m.isEmpty()) return null;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(m);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot serialise JSON", e);
        }
    }

    /**
     * Parse chuỗi JSON sang {@link java.util.Map}.
     *
     * @param s chuỗi JSON
     * @return map rỗng nếu chuỗi null/rỗng hoặc lỗi parse
     */
    private static Map<String, Object> parseJson(String s) {
        if (s == null || s.isBlank()) return Map.of();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(s, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            return Map.of();
        }
    }
}