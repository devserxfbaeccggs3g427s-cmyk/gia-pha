package com.familya.search.application.port.in;

import java.util.UUID;

public record StatisticsQuery(
        UUID treeId,
        UUID actingUser,
        long expectedTreeRevision,
        Long requestedWatermark) {
}
