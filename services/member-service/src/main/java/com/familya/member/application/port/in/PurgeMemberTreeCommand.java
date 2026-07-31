package com.familya.member.application.port.in;

import java.util.UUID;

/**
 * Saga command from Tree Access's delete-tree Saga. Tombstones every
 * non-tombstoned member in the tree and persists a compensation snapshot
 * for restoration before the irreversible boundary.
 */
public record PurgeMemberTreeCommand(
        UUID operationId,
        UUID treeId,
        long targetAggregateVersion,
        long targetEpoch) {
}