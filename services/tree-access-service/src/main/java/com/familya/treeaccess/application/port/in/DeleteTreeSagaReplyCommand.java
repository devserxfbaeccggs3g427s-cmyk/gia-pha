package com.familya.treeaccess.application.port.in;

import java.util.UUID;

public record DeleteTreeSagaReplyCommand(
        UUID operationId,
        String participantService,
        String stepCode,
        long appliedAggregateVersion,
        long appliedEpoch,
        boolean compensationApplied,
        boolean failed,
        String failureCode,
        String failureMessage) {
}