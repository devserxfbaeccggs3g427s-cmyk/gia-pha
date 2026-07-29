package com.familya.event.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event-service event base. Topic is {@code event.events.v1},
 * partitioned by treeId.
 */
public abstract class DomainEventChange {
    public abstract UUID treeId();
    public abstract UUID eventId();
    public abstract String eventType();
    public abstract int eventVersion();
    public abstract Instant occurredAt();
    public abstract long revision();

    public String topic() { return "event.events.v1"; }
    public String partitionKey() { return treeId().toString(); }
}