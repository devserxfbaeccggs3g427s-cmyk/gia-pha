package com.familya.media.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MediaRetentionRepository {
    void placeHold(UUID mediaId, UUID treeId, Instant holdUntil, String reason);

    void release(UUID holdId, Instant releasedAt);

    List<HoldRow> listReadyForCleanup(Instant now, int limit);

    /** Release any hold placed by {@code operationId}. Default no-op. */
    default int releaseHoldForOperation(UUID mediaId, UUID operationId) { return 0; }

    /** Bulk place retention holds on every media row in the tree. */
    default int bulkPlaceHoldByTree(UUID treeId, Instant holdUntil, String reason) { return 0; }

    record HoldRow(UUID id, UUID mediaId, UUID treeId, Instant holdUntil, String reason) { }
}
