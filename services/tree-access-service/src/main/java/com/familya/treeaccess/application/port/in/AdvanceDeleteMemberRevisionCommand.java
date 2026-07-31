package com.familya.treeaccess.application.port.in;

import java.util.UUID;

/**
 * Saga command from the Member service's delete-member Saga. Advances the
 * authoritative tree revision and epoch after every other participant has
 * acked. The tree is allowed to be in FROZEN or PENDING_DELETION state.
 */
public record AdvanceDeleteMemberRevisionCommand(
        UUID operationId,
        UUID treeId,
        long expectedTreeVersion,
        long newRevision,
        long newEpoch) {
}