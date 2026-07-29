package com.familya.media.domain.event;

import java.time.Instant;
import java.util.UUID;

public record MediaActivated(
        UUID treeId,
        UUID mediaId,
        long revision,
        Instant occurredAt,
        Instant activatedAt
) implements MediaChange {
    @Override public String eventType() { return "MediaActivated"; }
    @Override public int eventVersion() { return 1; }
    @Override public String topic() { return "media.events.v1"; }
    @Override public String partitionKey() { return treeId.toString(); }
}
