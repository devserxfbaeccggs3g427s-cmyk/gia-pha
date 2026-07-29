package com.familya.event.application.port.in;

import java.util.UUID;

public record PurgeEventTreeCommand(
        UUID operationId,
        UUID treeId,
        long targetAggregateVersion,
        long targetEpoch) {
}