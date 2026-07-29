package com.familya.relationship.application.port.out;

import java.util.Optional;
import java.util.UUID;

public interface MemberExistenceProjection {

    /**
     * Returns true if the member exists in the Member service's
     * projection and is not tombstoned. The Relationship service
     * refuses to create edges that point at a tombstoned or unknown
     * member — cross-service references use opaque IDs and must be
     * validated against the projection.
     */
    boolean isAvailable(UUID treeId, UUID memberId);

    /**
     * Returns false (dangling) if the projection has the member
     * tombstoned; absent if there is no projection row at all.
     */
    Optional<Boolean> tombstone(UUID treeId, UUID memberId);
}