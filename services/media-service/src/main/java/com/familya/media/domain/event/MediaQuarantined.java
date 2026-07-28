package com.familya.media.domain.event;

import java.time.Instant;
import java.util.UUID;

public record MediaQuarantined(
        UUID treeId,
        UUID mediaId,
        long revision,
        Instant occurredAt
) implements MediaChange {
    @Override public String eventType() { return "MediaQuarantined"; }
    @Override public int eventVersion() { return 1; }
    @Override public String topic() { return "media.events.v1"; }
    @Override public String partitionKey() { return treeId.toString(); }
}
