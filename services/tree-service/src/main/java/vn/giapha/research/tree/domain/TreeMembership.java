package vn.giapha.research.tree.domain;

import java.time.Instant;
import vn.giapha.research.tree.infrastructure.kernel.principal.TreeRole;

/** Membership of a user in a tree with a legacy-frozen role. */
public record TreeMembership(
        long treeKey,
        long userKey,
        String userExternalId,
        TreeRole role,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
