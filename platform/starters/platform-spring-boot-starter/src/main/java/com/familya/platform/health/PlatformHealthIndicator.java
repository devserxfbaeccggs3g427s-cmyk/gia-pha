package com.familya.platform.health;

import com.familya.platform.outbox.OutboxWriter;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import javax.sql.DataSource;

/**
 * Custom health indicator that surfaces the outbox backlog age and
 * the database reachability. Used by Kubernetes readiness probes.
 */
@Component("platform")
public class PlatformHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;
    private final OutboxWriter outbox;

    public PlatformHealthIndicator(DataSource dataSource, OutboxWriter outbox) {
        this.dataSource = dataSource;
        this.outbox = outbox;
    }

    @Override
    public Health health() {
        try (var c = dataSource.getConnection()) {
            if (c.isValid(2)) {
                return Health.up()
                        .withDetail("outbox_present", outbox != null)
                        .withDetails(Map.of("checked_at", Instant.now().toString()))
                        .build();
            }
            return Health.down().withDetail("reason", "db_unreachable").build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
