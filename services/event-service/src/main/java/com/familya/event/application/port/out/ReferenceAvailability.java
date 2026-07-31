package com.familya.event.application.port.out;

import java.util.Set;
import java.util.UUID;

/**
 * Reference availability projection. The Event service consumes
 * member/media events into local projections and consults this port
 * before accepting a mutation that points at a member or media
 * object. Cross-service foreign keys are forbidden; references are
 * opaque IDs validated against the local projection.
 */
public interface ReferenceAvailability {

    boolean isMemberAvailable(UUID treeId, UUID memberId);

    boolean isMediaAvailable(UUID treeId, UUID mediaId);

    /**
     * Returns the set of dangling references from the given list.
     * Used by the reconciliation endpoint to surface dangling
     * references without rejecting them outright.
     */
    Set<UUID> danglingMembers(UUID treeId, java.util.Collection<UUID> memberIds);

    Set<UUID> danglingMedia(UUID treeId, java.util.Collection<UUID> mediaIds);
}