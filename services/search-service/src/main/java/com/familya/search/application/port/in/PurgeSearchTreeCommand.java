package com.familya.search.application.port.in;

import java.util.UUID;

public record PurgeSearchTreeCommand(
        UUID operationId,
        UUID treeId,
        long targetAggregateVersion,
        long targetEpoch) {
}