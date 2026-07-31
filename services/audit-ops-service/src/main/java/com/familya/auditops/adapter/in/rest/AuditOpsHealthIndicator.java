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
 *   <li>{@code projection.freshness-seconds}: tuổi lớn nhất của
 *       watermark trên các topic projection; dùng để cảnh báo lag.</li>
 * </ul>
 */
package com.familya.auditops.adapter.in.rest;

import com.familya.auditops.adapter.out.persistence.JdbcOperationLifecycleProjection;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Component("auditOpsHealth")
public class AuditOpsHealthIndicator implements HealthIndicator {

    private static final Duration OUTBOX_WARN = Duration.ofSeconds(60);
    private static final Duration OUTBOX_DOWN = Duration.ofSeconds(600);
    private static final Duration PROJECTION_LAG_WARN = Duration.ofSeconds(120);
    private static final Duration PROJECTION_LAG_DOWN = Duration.ofSeconds(600);

    private final NamedParameterJdbcTemplate jdbc;
    private final JdbcOperationLifecycleProjection projection;

    public AuditOpsHealthIndicator(NamedParameterJdbcTemplate jdbc, JdbcOperationLifecycleProjection projection) {
        this.jdbc = jdbc;
        this.projection = projection;
    }

    @Override
    public Health health() {
        long outboxAgeSeconds = oldestOutboxAgeSeconds();
        long manualReview = projection.countByState("MANUAL_REVIEW");
        long running = projection.countByState("RUNNING")
                + projection.countByState("PENDING")
                + projection.countByState("COMPENSATING");
        long projectionLagSeconds = oldestProjectionWatermarkSeconds();

        Health.Builder b = downOrWarn(outboxAgeSeconds, OUTBOX_DOWN, OUTBOX_WARN);
        if (projectionLagSeconds >= PROJECTION_LAG_DOWN.toSeconds()) {
            b = b == Health.down() ? b : Health.down();
        } else if (projectionLagSeconds >= PROJECTION_LAG_WARN.toSeconds() && b.build().getStatus() == Status.UP) {
            b = Health.status("WARN");
        }
        b.withDetail("outbox.age-seconds", outboxAgeSeconds)
                .withDetail("manual_review.count", manualReview)
                .withDetail("running.count", running)
                .withDetail("projection.freshness-seconds", projectionLagSeconds);
        return b.build();
    }

    private long oldestOutboxAgeSeconds() {
        var rows = jdbc.queryForList(
                "SELECT MIN(occurred_at) AS oldest FROM outbox_record WHERE published_at IS NULL",
                new MapSqlParameterSource());
        if (rows.isEmpty()) return 0L;
        Object oldest = rows.get(0).get("oldest");
        if (oldest == null) return 0L;
        Instant occurred = ((Timestamp) oldest).toInstant();
        return Math.max(0L, Duration.between(occurred, Instant.now()).toSeconds());
    }

    private long oldestProjectionWatermarkSeconds() {
        var rows = jdbc.queryForList(
                "SELECT MAX(last_seen_at) AS latest FROM projection_watermark",
                new MapSqlParameterSource());
        if (rows.isEmpty()) return 0L;
        Object latest = rows.get(0).get("latest");
        if (latest == null) return 0L;
        Instant t = ((Timestamp) latest).toInstant();
        return Math.max(0L, Duration.between(t, Instant.now()).toSeconds());
    }

    private static Health.Builder downOrWarn(long ageSeconds, Duration down, Duration warn) {
        if (ageSeconds >= down.toSeconds()) return Health.down();
        if (ageSeconds >= warn.toSeconds()) return Health.status("WARN");
        return Health.up();
    }
}
