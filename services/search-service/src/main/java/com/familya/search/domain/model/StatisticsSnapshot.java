package com.familya.search.domain.model;

import java.time.Instant;
import java.util.UUID;

public record StatisticsSnapshot(
        UUID treeId,
        long memberCount,
        int generations,
        long eventsCount,
        long mediaCount,
        Instant computedAt,
        long watermark
) { }
