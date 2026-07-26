package vn.giapha.research.tree.application.query;

/**
 * Explicit read projection with per-tree entity counts, used by tree lists
 * and statistics without hydrating aggregates (Task 10.1).
 */
public record TreeStats(
        long treeKey,
        long memberCount,
        long relationshipCount,
        long eventCount,
        long mediaCount,
        long albumCount) {
}
