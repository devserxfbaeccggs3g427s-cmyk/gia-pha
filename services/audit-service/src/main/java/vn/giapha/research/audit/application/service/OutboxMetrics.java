package vn.giapha.research.audit.application.service;

import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import vn.giapha.research.audit.domain.model.OutboxQueueStats;

/**
 * Outbox queue telemetry (Task 12.5, Requirement 13). Gauges are backed by
 * atomics refreshed each relay cycle — scrapes never touch the database.
 *
 * <p>Alerting guidance (see {@code docs/migration/ops/audit-outbox-operations.md}):
 * page when {@code giapha.outbox.ready.age.seconds} exceeds 300, warn when
 * {@code giapha.outbox.dead} grows, warn on a sustained non-zero
 * {@code giapha.outbox.retried} rate.
 */
@Component
public class OutboxMetrics {

    private final AtomicLong readyCount = new AtomicLong();
    private final AtomicLong inProgressCount = new AtomicLong();
    private final AtomicLong deadCount = new AtomicLong();
    private final AtomicLong oldestReadyAgeSeconds = new AtomicLong();

    private final Counter completed;
    private final Counter retried;
    private final Counter deadLettered;

    public OutboxMetrics(MeterRegistry registry) {
        Gauge.builder("giapha.outbox.ready", readyCount, AtomicLong::get)
                .description("Outbox events ready to be claimed")
                .register(registry);
        Gauge.builder("giapha.outbox.in.progress", inProgressCount, AtomicLong::get)
                .description("Outbox events under a live lease")
                .register(registry);
        Gauge.builder("giapha.outbox.dead", deadCount, AtomicLong::get)
                .description("Dead-lettered outbox events awaiting operator action")
                .register(registry);
        Gauge.builder("giapha.outbox.ready.age.seconds", oldestReadyAgeSeconds, AtomicLong::get)
                .description("Age of the oldest ready outbox event")
                .register(registry);
        this.completed = Counter.builder("giapha.outbox.completed")
                .description("Outbox events handled successfully")
                .register(registry);
        this.retried = Counter.builder("giapha.outbox.retried")
                .description("Outbox handler failures scheduled for retry")
                .register(registry);
        this.deadLettered = Counter.builder("giapha.outbox.dead.lettered")
                .description("Outbox events moved to the dead letter state")
                .register(registry);
    }

    public void update(OutboxQueueStats stats) {
        readyCount.set(stats.readyCount());
        inProgressCount.set(stats.inProgressCount());
        deadCount.set(stats.deadCount());
        oldestReadyAgeSeconds.set(stats.oldestReadyAgeSeconds());
    }

    public void completed() {
        completed.increment();
    }

    public void retried() {
        retried.increment();
    }

    public void deadLettered() {
        deadLettered.increment();
    }
}
