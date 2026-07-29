package com.familya.media.application.port.in;

import java.util.UUID;

public record PurgeMediaMetadataTreeCommand(
        UUID operationId,
        UUID treeId,
        boolean placeRetentionHolds,
        long targetAggregateVersion,
        long targetEpoch) {
}