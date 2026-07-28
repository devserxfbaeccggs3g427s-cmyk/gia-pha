package com.familya.search.domain.model;

import java.time.Instant;
import java.util.UUID;

public record ReportSnapshot(
        UUID id,
        UUID treeId,
        Kind kind,
        String payload,
        long watermark,
        Instant computedAt
) {
    public enum Kind { DEMOGRAPHICS, MEDIA_SUMMARY, EVENT_TIMELINE }
}
