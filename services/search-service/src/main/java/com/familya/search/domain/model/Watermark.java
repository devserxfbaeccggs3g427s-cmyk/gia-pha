package com.familya.search.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Watermark(
        UUID treeId,
        Domain domain,
        long value,
        Instant lastUpdated
) {
    public enum Domain { MEMBER, RELATIONSHIP, EVENT, MEDIA, TREE }
}
