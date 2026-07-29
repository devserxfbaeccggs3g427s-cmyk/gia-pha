package com.familya.treeaccess.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Membership event base. Topic is {@code tree.memberships.v1} so
 * downstream services can subscribe to a single ordered stream of
 * membership changes per tree. Partition key is treeId.
 */
public abstract class MembershipEvent {
    public abstract UUID treeId();
    public abstract UUID userId();
    public abstract String eventType();
    public abstract int eventVersion();
    public abstract Instant occurredAt();

    public String topic() { return "tree.memberships.v1"; }
    public String partitionKey() { return treeId().toString(); }
}