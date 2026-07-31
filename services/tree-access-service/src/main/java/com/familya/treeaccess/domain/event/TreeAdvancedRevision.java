package com.familya.treeaccess.domain.event;

import java.time.Instant;
import java.util.UUID;

public final class TreeAdvancedRevision extends TreeEvent {
    private final UUID treeId;
    private final long revision;
    private final long epoch;
    private final UUID advancedBy;
    private final String reason;
    private final Instant occurredAt;

    /**
     * @param treeId      mã cây
     * @param revision    revision sau khi tăng
     * @param epoch       epoch sau khi tăng
     * @param advancedBy  UUID người/thực thể yêu cầu tăng (orchestrator)
     * @param reason      lý do tăng revision
     * @param occurredAt  thời điểm phát sinh
     */
    public TreeAdvancedRevision(UUID treeId, long revision, long epoch, UUID advancedBy,
                                 String reason, Instant occurredAt) {
        this.treeId = treeId;
        this.revision = revision;
        this.epoch = epoch;
        this.advancedBy = advancedBy;
        this.reason = reason;
        this.occurredAt = occurredAt;
    }

    @Override public UUID treeId() { return treeId; }
    @Override public long revision() { return revision; }
    @Override public long epoch() { return epoch; }
    @Override public String eventType() { return "TreeAdvancedRevision"; }
    @Override public int eventVersion() { return 1; }
    @Override public Instant occurredAt() { return occurredAt; }

    /**
     * @return UUID thực thể đã yêu cầu tăng revision
     */
    public UUID advancedBy() { return advancedBy; }
    /**
     * @return lý do tăng revision
     */
    public String reason() { return reason; }
}