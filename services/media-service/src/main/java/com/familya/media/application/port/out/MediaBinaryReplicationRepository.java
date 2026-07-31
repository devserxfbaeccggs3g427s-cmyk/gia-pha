package com.familya.media.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MediaBinaryReplicationRepository {
    void record(UUID mediaId, String sourceRegion, String targetRegion, String sha256, String status, Instant at);

    List<ReplicationRow> listForMedia(UUID mediaId);

    record ReplicationRow(UUID id, UUID mediaId, String sourceRegion, String targetRegion, String sha256, String status, Instant lastAttemptAt) { }
}
