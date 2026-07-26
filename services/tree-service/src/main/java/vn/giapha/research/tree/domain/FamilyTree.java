package vn.giapha.research.tree.domain;

import java.time.Instant;

/**
 * Family tree aggregate root. {@code treeKey} is the internal surrogate key
 * and the lock anchor for all graph mutations; {@code externalId} is the
 * unchanged legacy nanoid exposed by the API. {@code revision} increments on
 * every content mutation and backs ETag/cache invalidation (ADR-015).
 */
public record FamilyTree(
        long treeKey,
        String externalId,
        long ownerUserKey,
        String name,
        String description,
        long revision,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
