package com.familya.media.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MediaRetentionRepository {
    void placeHold(UUID mediaId, UUID treeId, Instant holdUntil, String reason);

    void release(UUID holdId, Instant releasedAt);

    List<HoldRow> listReadyForCleanup(Instant now, int limit);

    record HoldRow(UUID id, UUID mediaId, UUID treeId, Instant holdUntil, String reason) { }
}
