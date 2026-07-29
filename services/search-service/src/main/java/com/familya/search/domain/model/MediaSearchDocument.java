package com.familya.search.domain.model;

import java.time.Instant;
import java.util.UUID;

public record MediaSearchDocument(
        UUID treeId,
        UUID mediaId,
        String filename,
        String kind,
        boolean tombstoned,
        Instant lastUpdated
) { }
