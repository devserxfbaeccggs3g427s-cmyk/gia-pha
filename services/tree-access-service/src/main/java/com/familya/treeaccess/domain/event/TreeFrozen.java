package com.familya.treeaccess.domain.event;

import java.time.Instant;
import java.util.UUID;

public final class TreeFrozen extends TreeEvent {
    private final UUID treeId;
    private final long revision;
    private final long epoch;
    private final Instant occurredAt;

    /**
     * @param treeId     mã cây
     * @param revision   revision tại thời điểm đóng băng
     * @param epoch      epoch tại thời điểm đóng băng
     * @param occurredAt thời điểm phát sinh
     */
    public TreeFrozen(UUID treeId, long revision, long epoch, Instant occurredAt) {
        this.treeId = treeId;
        this.revision = revision;
        this.epoch = epoch;
        this.occurredAt = occurredAt;
    }

    @Override public UUID treeId() { return treeId; }
    @Override public long revision() { return revision; }
    @Override public long epoch() { return epoch; }
    @Override public String eventType() { return "TreeFrozen"; }
    @Override public int eventVersion() { return 1; }
    @Override public Instant occurredAt() { return occurredAt; }
}