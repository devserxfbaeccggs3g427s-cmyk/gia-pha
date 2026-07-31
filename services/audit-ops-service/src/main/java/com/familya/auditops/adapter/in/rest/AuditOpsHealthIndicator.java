/**
 * Health indicator ở cấp dịch vụ cho projection của audit-ops.
 *
 * <p>Báo cáo các chỉ số sau để actuator {@code /actuator/health} có
 * thể giám sát:</p>
 * <ul>
 *   <li>{@code outbox.age-seconds}: tuổi của outbox row chưa được
 *       publish. Trạng thái WARN khi &gt; 60 giây, DOWN khi &gt; 600 giây.</li>
 *   <li>{@code manual_review.count}: số operation đang chờ operator review.</li>
 *   <li>{@code running.count}: số operation đang in-flight, phục vụ
 *       quy tắc cutover auto-stop (Task 19).</li>
 * </ul>
 */
package com.familya.auditops.adapter.in.rest;

import com.familya.auditops.application.port.out.OperationRepository;
import com.familya.auditops.domain.model.OperationStatus;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Triển khai {@link HealthIndicator} để theo dõi độ trễ của outbox và
 * trạng thái tổng quát của các operation.
 *
 * <p>Các ngưỡng cảnh báo được khai báo dưới dạng hằng số {@link Duration}
 * để dễ dàng điều chỉnh và kiểm thử.</p>
 */
@Component("auditOpsHealth")
public class AuditOpsHealthIndicator implements HealthIndicator {

    /** Ngưỡng WARN: tuổi outbox vượt 60 giây. */
    private static final Duration OUTBOX_WARN = Duration.ofSeconds(60);
    /** Ngưỡng DOWN: tuổi outbox vượt 600 giây (10 phút). */
    private static final Duration OUTBOX_DOWN = Duration.ofSeconds(600);

    /** JDBC template để truy vấn bảng {@code outbox_record}. */
    private final NamedParameterJdbcTemplate jdbc;
    /** Repository thống kê operation theo trạng thái. */
    private final OperationRepository operations;

    /**
     * Khởi tạo indicator với các phụ thuộc cần thiết.
     *
     * @param jdbc       JDBC template
     * @param operations repository operation
     */
    public AuditOpsHealthIndicator(NamedParameterJdbcTemplate jdbc, OperationRepository operations) {
        this.jdbc = jdbc;
        this.operations = operations;
    }

    /**
     * Tính toán trạng thái sức khoẻ hiện tại.
     *
     * <p>Logic xác định trạng thái:</p>
     * <ol>
     *   <li>Tính tuổi outbox; nếu &gt;= 600s thì DOWN.</li>
     *   <li>Nếu &gt;= 60s thì WARN.</li>
     *   <li>Mặc định UP.</li>
     * </ol>
     *
     * @return {@link Health} với các chi tiết kèm theo
     */
    @Override
    public Health health() {
        // Tính tuổi của bản ghi outbox chưa publish lâu nhất.
        long outboxAgeSeconds = oldestOutboxAgeSeconds();
        // Đếm số operation đang chờ operator xử lý.
        long manualReview = operations.countByStatusIn(List.of(OperationStatus.MANUAL_REVIEW));
        // Đếm tổng số operation đang in-flight (PENDING/RUNNING/COMPENSATING).
        long running = operations.countByStatusIn(List.of(OperationStatus.RUNNING, OperationStatus.PENDING, OperationStatus.COMPENSATING));

        // Chọn trạng thái dựa trên tuổi outbox.
        Health.Builder b = outboxAgeSeconds >= OUTBOX_DOWN.toSeconds()
                ? Health.down()
                : outboxAgeSeconds >= OUTBOX_WARN.toSeconds() ? Health.status("WARN") : Health.up();

        // Đính kèm các chỉ số để dashboard và alerting có thể sử dụng.
        b.withDetail("outbox.age-seconds", outboxAgeSeconds)
                .withDetail("manual_review.count", manualReview)
                .withDetail("running.count", running);
        return b.build();
    }

    /**
     * Tính tuổi (giây) của bản ghi {@code outbox_record} chưa được publish
     * có {@code occurred_at} nhỏ nhất.
     *
     * @return tuổi tính bằng giây (0 nếu bảng rỗng hoặc không có row chưa publish)
     */
    private long oldestOutboxAgeSeconds() {
        // Truy vấn MIN(occurred_at) trên các row chưa publish.
        var rows = jdbc.queryForList(
                "SELECT MIN(occurred_at) AS oldest FROM outbox_record WHERE published_at IS NULL",
                new MapSqlParameterSource());
        if (rows.isEmpty()) return 0L;
        Object oldest = rows.get(0).get("oldest");
        // Không có row nào chưa publish -> trả về 0.
        if (oldest == null) return 0L;
        Instant occurred = ((Timestamp) oldest).toInstant();
        // Math.max để đề phòng clock lệch (occurred > now).
        return Math.max(0L, Duration.between(occurred, Instant.now()).toSeconds());
    }
}