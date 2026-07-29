package com.familya.treeaccess.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Tree event base. The topic is derived from the bounded context
 * (tree.events.v1) so all tree events partition by treeId. The
 * partition key is required for downstream ordering.
 */
public abstract class TreeEvent {
    public abstract UUID treeId();
    public abstract long revision();
    public abstract long epoch();
    public abstract String eventType();
    public abstract int eventVersion();
    public abstract Instant occurredAt();

    public String topic() { return "tree.events.v1"; }
    public String partitionKey() { return treeId().toString(); }
}