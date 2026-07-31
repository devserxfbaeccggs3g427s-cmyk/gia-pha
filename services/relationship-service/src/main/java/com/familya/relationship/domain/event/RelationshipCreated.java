package com.familya.relationship.domain.event;

import com.familya.relationship.domain.model.Relationship;

import java.time.Instant;
import java.util.UUID;

public final class RelationshipCreated extends RelationshipEvent {
    private final UUID relationshipId;
    private final UUID treeId;
    private final Relationship.Kind kind;
    private final UUID fromMemberId;
    private final UUID toMemberId;
    private final String metadataJson;
    private final long commandSeq;
    private final Instant occurredAt;

    public RelationshipCreated(UUID relationshipId, UUID treeId, Relationship.Kind kind,
                                UUID fromMemberId, UUID toMemberId, String metadataJson,
                                long commandSeq, Instant occurredAt) {
        this.relationshipId = relationshipId;
        this.treeId = treeId;
        this.kind = kind;
        this.fromMemberId = fromMemberId;
        this.toMemberId = toMemberId;
        this.metadataJson = metadataJson;
        this.commandSeq = commandSeq;
        this.occurredAt = occurredAt;
    }

    @Override public UUID treeId() { return treeId; }
    @Override public String eventType() { return "RelationshipCreated"; }
    @Override public int eventVersion() { return 1; }
    @Override public Instant occurredAt() { return occurredAt; }
    @Override public long commandSeq() { return commandSeq; }

    public UUID relationshipId() { return relationshipId; }
    public Relationship.Kind kind() { return kind; }
    public UUID fromMemberId() { return fromMemberId; }
    public UUID toMemberId() { return toMemberId; }
    public String metadataJson() { return metadataJson; }
}