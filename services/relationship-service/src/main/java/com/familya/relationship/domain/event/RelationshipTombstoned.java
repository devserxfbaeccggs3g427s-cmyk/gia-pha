package com.familya.relationship.domain.event;

import java.time.Instant;
import java.util.UUID;

public final class RelationshipTombstoned extends RelationshipEvent {
    private final UUID relationshipId;
    private final UUID treeId;
    private final long commandSeq;
    private final Instant occurredAt;

    public RelationshipTombstoned(UUID relationshipId, UUID treeId, long commandSeq, Instant occurredAt) {
        this.relationshipId = relationshipId;
        this.treeId = treeId;
        this.commandSeq = commandSeq;
        this.occurredAt = occurredAt;
    }

    @Override public UUID treeId() { return treeId; }
    @Override public String eventType() { return "RelationshipTombstoned"; }
    @Override public int eventVersion() { return 1; }
    @Override public Instant occurredAt() { return occurredAt; }
    @Override public long commandSeq() { return commandSeq; }

    public UUID relationshipId() { return relationshipId; }
}