package com.familya.media.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Album(
        UUID id,
        UUID treeId,
        String name,
        String description,
        UUID coverMediaId,
        Instant createdAt,
        Instant updatedAt,
        long version,
        Instant tombstonedAt
) { }
