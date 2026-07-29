package com.familya.sharing.application.port.in;

import java.util.UUID;

public record RevokeSharingTreeCommand(
        UUID operationId,
        UUID treeId,
        long targetAggregateVersion,
        long targetEpoch) {
}