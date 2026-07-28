package com.familya.member.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Member event base. Topic is {@code member.events.v1}, partitioned by
 * treeId so consumers can subscribe to a single ordered stream of
 * member changes per tree.
 */
public abstract class MemberEvent {
    public abstract UUID treeId();
    public abstract UUID memberId();
    public abstract String eventType();
    public abstract int eventVersion();
    public abstract Instant occurredAt();

    public String topic() { return "member.events.v1"; }
    public String partitionKey() { return treeId().toString(); }
}