package com.familya.treeaccess.domain.event;

import java.time.Instant;
import java.util.UUID;

public final class TreeCreated extends TreeEvent {
    private final UUID treeId;
    private final UUID ownerUserId;
    private final String name;
    private final long revision;
    private final long epoch;
    private final Instant occurredAt;

    public TreeCreated(UUID treeId, UUID ownerUserId, String name, long revision, long epoch, Instant occurredAt) {
        this.treeId = treeId;
        this.ownerUserId = ownerUserId;
        this.name = name;
        this.revision = revision;
        this.epoch = epoch;
        this.occurredAt = occurredAt;
    }

    @Override public UUID treeId() { return treeId; }
    @Override public long revision() { return revision; }
    @Override public long epoch() { return epoch; }
    @Override public String eventType() { return "TreeCreated"; }
    @Override public int eventVersion() { return 1; }
    @Override public Instant occurredAt() { return occurredAt; }

    public UUID ownerUserId() { return ownerUserId; }
    public String name() { return name; }
}