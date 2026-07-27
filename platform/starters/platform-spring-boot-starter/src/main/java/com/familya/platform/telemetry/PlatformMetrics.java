package com.familya.platform.telemetry;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Standard metric names used by every Family Tree service. Having a
 * single, well-known set of names makes dashboards and alerts
 * identical across services.
 */
@Component
public class PlatformMetrics {

    private final MeterRegistry registry;

    public PlatformMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void mutationAccepted(String service, String operation) {
        registry.counter("familya.mutation.accepted", Tags.of("service", service, "operation", operation)).increment();
    }

    public void mutationFailed(String service, String operation, String code) {
        registry.counter("familya.mutation.failed", Tags.of("service", service, "operation", operation, "code", code)).increment();
    }

    public Timer mutationLatency(String service, String operation) {
        return registry.timer("familya.mutation.latency", Tags.of("service", service, "operation", operation));
    }

    public void outboxStaged(String service, String eventType) {
        registry.counter("familya.outbox.staged", Tags.of("service", service, "event_type", eventType)).increment();
    }

    public void outboxPublished(String service, String eventType) {
        registry.counter("familya.outbox.published", Tags.of("service", service, "event_type", eventType)).increment();
    }

    public void outboxPublishFailed(String service, String eventType) {
        registry.counter("familya.outbox.publish_failed", Tags.of("service", service, "event_type", eventType)).increment();
    }

    public void consumerProcessed(String service, String eventType) {
        registry.counter("familya.consumer.processed", Tags.of("service", service, "event_type", eventType)).increment();
    }

    public void consumerDuplicate(String service, String eventType) {
        registry.counter("familya.consumer.duplicate", Tags.of("service", service, "event_type", eventType)).increment();
    }

    public void projectionStale(String service, String projection, long ageSeconds) {
        registry.counter("familya.projection.stale", Tags.of("service", service, "projection", projection, "age_seconds", String.valueOf(ageSeconds))).increment();
    }

    public Counter mutationAcceptedCounter(String service, String operation) {
        return registry.counter("familya.mutation.accepted", Tags.of("service", service, "operation", operation));
    }
}
