package com.familya.platform.projection;

import java.time.Instant;
import java.util.UUID;

/**
 * Generic projection header. Every service-owned read model carries:
 * - aggregate identifier (treeId, memberId, …)
 * - source aggregate revision/epoch (the projection knows the
 *   authoritative source's version)
 * - last updated timestamp (for freshness checks)
 * - watermark per source topic (for replay and gap detection)
 * - origin event id (for inbox dedup tie-back)
 *
 * <p>This is the minimum metadata a service needs to make a safe
 * authorization decision or to reconcile against the source service.
 * It is intentionally framework-independent so the projection SDK can
 * be reused across Member, Relationship, Event, Media, Sharing,
 * Search, and Transfer services.</p>
 */
public record ProjectionHeader(
        UUID aggregateId,
        long revision,
        long epoch,
        Instant lastUpdatedAt,
        long watermark,
        String originEventId
) {
    public ProjectionHeader withRevision(long newRevision) {
        return new ProjectionHeader(aggregateId, newRevision, epoch, lastUpdatedAt, watermark, originEventId);
    }
}