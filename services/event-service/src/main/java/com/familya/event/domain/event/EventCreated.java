package com.familya.event.domain.event;

import com.familya.event.domain.model.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public final class EventCreated extends DomainEventChange {
    private final UUID eventId;
    private final UUID treeId;
    private final DomainEvent.Kind kind;
    private final String title;
    private final long revision;
    private final Instant occurredAt;

    public EventCreated(UUID eventId, UUID treeId, DomainEvent.Kind kind, String title,
                        long revision, Instant occurredAt) {
        this.eventId = eventId;
        this.treeId = treeId;
        this.kind = kind;
        this.title = title;
        this.revision = revision;
        this.occurredAt = occurredAt;
    }

    @Override public UUID treeId() { return treeId; }
    @Override public UUID eventId() { return eventId; }
    @Override public String eventType() { return "EventCreated"; }
    @Override public int eventVersion() { return 1; }
    @Override public Instant occurredAt() { return occurredAt; }
    @Override public long revision() { return revision; }

    public DomainEvent.Kind kind() { return kind; }
    public String title() { return title; }
}