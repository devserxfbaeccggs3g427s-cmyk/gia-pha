package com.familya.media.application.port.in;

import java.util.UUID;

/**
 * Saga command from the Member service's delete-member Saga. Detaches every
 * MEMBER-kind reference that points to the deleted member. Physical binary
 * deletion is NOT part of this Saga and is performed by the delayed cleanup
 * worker under retention holds.
 */
public record DetachMemberMediaReferencesCommand(
        UUID operationId,
        UUID treeId,
        UUID memberId,
        long targetAggregateVersion,
        long targetEpoch) {
}