package com.familya.relationship.application.port.in;

import java.util.UUID;

/**
 * Saga-driven command from the Member service's delete-member Saga. The
 * Relationship service MUST tombstone every active edge that touches the
 * member, persist a compensation snapshot for restoration, and reply with
 * the applied aggregate version and epoch.
 */
public record DisableMemberRelationshipsCommand(
        UUID operationId,
        UUID treeId,
        UUID memberId,
        long expectedAggregateVersion,
        long expectedEpoch,
        long targetAggregateVersion,
        long targetEpoch) {
}