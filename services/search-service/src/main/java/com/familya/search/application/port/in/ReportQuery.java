package com.familya.search.application.port.in;

import java.util.UUID;

public record ReportQuery(
        UUID treeId,
        UUID actingUser,
        long expectedTreeRevision,
        String kind,
        Long requestedWatermark) {
}
