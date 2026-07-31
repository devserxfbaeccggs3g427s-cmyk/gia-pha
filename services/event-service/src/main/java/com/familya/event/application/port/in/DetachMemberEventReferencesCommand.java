package com.familya.event.application.port.in;

import java.util.UUID;

/**
 * Saga command from the Member service's delete-member Saga. Detaches the
 * member from primary and additional references on every event that touches
 * it, while keeping the event itself alive.
 */
public record DetachMemberEventReferencesCommand(
        UUID operationId,
        UUID treeId,
        UUID memberId,
        long targetAggregateVersion,
        long targetEpoch) {
}