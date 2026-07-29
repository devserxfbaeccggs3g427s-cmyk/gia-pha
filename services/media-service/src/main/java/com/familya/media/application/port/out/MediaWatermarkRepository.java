package com.familya.media.application.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Per-tree per-domain watermark. Each mutation advances the
 * watermark atomically; reconciliation replays deltas by reading
 * rows whose {@code updated_at} sits after the stored watermark.
 */
public interface MediaWatermarkRepository {

    Optional<long[]> read(UUID treeId, String domain);

    long advance(UUID treeId, String domain, long newWatermark, java.time.Instant now);

    record Watermark(UUID treeId, String domain, long watermark, java.time.Instant lastUpdated) { }
}
