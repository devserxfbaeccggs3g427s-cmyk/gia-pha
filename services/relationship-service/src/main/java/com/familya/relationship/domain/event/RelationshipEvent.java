package com.familya.relationship.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Relationship event base. Topic is {@code relationship.events.v1},
 * partitioned by treeId. The graph serializer relies on this
 * partition key to order commands per tree.
 */
public abstract class RelationshipEvent {
    public abstract UUID treeId();
    public abstract String eventType();
    public abstract int eventVersion();
    public abstract Instant occurredAt();
    public abstract long commandSeq();

    public String topic() { return "relationship.events.v1"; }
    public String partitionKey() { return treeId().toString(); }
}