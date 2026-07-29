package com.familya.relationship.application.port.in;

import java.util.UUID;

/**
 * Saga command from Tree Access's delete-tree Saga. Tombstones every
 * non-tombstoned relationship in the tree and persists a compensation
 * snapshot.
 */
public record PurgeRelationshipTreeCommand(
        UUID operationId,
        UUID treeId,
        long targetAggregateVersion,
        long targetEpoch) {
}