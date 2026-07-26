package vn.giapha.research.media.domain;

import java.time.Instant;

/** Album aggregate (legacy `Album`). */
public record Album(
        long albumKey,
        long treeKey,
        String externalId,
        String title,
        String description,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
