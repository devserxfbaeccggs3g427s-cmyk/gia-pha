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
 * Service-level health for the audit-ops projection. Reports:
 *
 * <ul>
 *   <li>{@code outbox.age-seconds}: oldest unpublished outbox row.
 *       Trips WARN at &gt; 60s, DOWN at &gt; 600s.</li>
 *   <li>{@code manual_review.count}: backlog of operations awaiting
 *       operator review.</li>
 *   <li>{@code running.count}: in-flight operations, exposed for the
 *       cutover auto-stop rule (Task 19).</li>
 * </ul>
 */
@Component("auditOpsHealth")
public class AuditOpsHealthIndicator implements HealthIndicator {

    private static final Duration OUTBOX_WARN = Duration.ofSeconds(60);
    private static final Duration OUTBOX_DOWN = Duration.ofSeconds(600);

    private final NamedParameterJdbcTemplate jdbc;
    private final OperationRepository operations;

    public AuditOpsHealthIndicator(NamedParameterJdbcTemplate jdbc, OperationRepository operations) {
        this.jdbc = jdbc;
        this.operations = operations;
    }

    @Override
    public Health health() {
        long outboxAgeSeconds = oldestOutboxAgeSeconds();
        long manualReview = operations.countByStatusIn(List.of(OperationStatus.MANUAL_REVIEW));
        long running = operations.countByStatusIn(List.of(OperationStatus.RUNNING, OperationStatus.PENDING, OperationStatus.COMPENSATING));

        Health.Builder b = outboxAgeSeconds >= OUTBOX_DOWN.toSeconds()
                ? Health.down()
                : outboxAgeSeconds >= OUTBOX_WARN.toSeconds() ? Health.status("WARN") : Health.up();

        b.withDetail("outbox.age-seconds", outboxAgeSeconds)
                .withDetail("manual_review.count", manualReview)
                .withDetail("running.count", running);
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
}