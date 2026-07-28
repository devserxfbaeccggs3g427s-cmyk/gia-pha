package com.familya.media.domain.event;

import java.time.Instant;
import java.util.UUID;

public sealed interface MediaChange
        permits MediaQuarantined, MediaScanned, MediaAssociated, MediaDetached, AlbumCreated {

    UUID treeId();
    UUID mediaId();
    String eventType();
    int eventVersion();
    long revision();
    Instant occurredAt();
    String topic();
    String partitionKey();
}
