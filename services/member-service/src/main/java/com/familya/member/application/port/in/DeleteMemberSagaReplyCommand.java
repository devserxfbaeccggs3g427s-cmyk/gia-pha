package com.familya.member.application.port.in;

import java.util.UUID;

/**
 * Input for processing a Saga reply. The owner (Member service) is the only
 * consumer of these replies for the delete-member Saga.
 */
public record DeleteMemberSagaReplyCommand(
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