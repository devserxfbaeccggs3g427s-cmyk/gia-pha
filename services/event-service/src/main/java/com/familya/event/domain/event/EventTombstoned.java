package com.familya.event.domain.event;

import java.time.Instant;
import java.util.UUID;

public final class EventTombstoned extends DomainEventChange {
    private final UUID eventId;
    private final UUID treeId;
    private final long revision;
    private final Instant occurredAt;

    public EventTombstoned(UUID eventId, UUID treeId, long revision, Instant occurredAt) {
        this.eventId = eventId;
        this.treeId = treeId;
        this.revision = revision;
        this.occurredAt = occurredAt;
    }

    @Override public UUID treeId() { return treeId; }
    @Override public UUID eventId() { return eventId; }
    @Override public String eventType() { return "EventTombstoned"; }
    @Override public int eventVersion() { return 1; }
    @Override public Instant occurredAt() { return occurredAt; }
    @Override public long revision() { return revision; }
}