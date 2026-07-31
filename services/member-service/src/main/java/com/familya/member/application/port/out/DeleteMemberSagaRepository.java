package com.familya.member.application.port.out;

import com.familya.member.domain.model.DeleteMemberSagaState;
import com.familya.member.domain.model.DeleteMemberSagaStep;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeleteMemberSagaRepository {

    void saveState(DeleteMemberSagaState state);

    Optional<DeleteMemberSagaState> findState(UUID operationId);

    void saveSteps(List<DeleteMemberSagaStep> steps);

    List<DeleteMemberSagaStep> listSteps(UUID operationId);

    void updateStep(DeleteMemberSagaStep step);

    void saveCompensationSnapshot(UUID operationId, String participantService, String snapshotJson);

    Optional<String> loadCompensationSnapshot(UUID operationId, String participantService);

    List<DeleteMemberSagaState> listDispatchedPastDeadline();

    List<DeleteMemberSagaStep> listRetryableSteps(Instant now, int limit);

    List<DeleteMemberSagaStep> listTimedOutSteps(Instant now, int limit);

    boolean tryClaimDispatch(UUID operationId, int sequenceNo, UUID dispatchToken, Instant now, Instant stepDeadlineAt);

    boolean releaseOrScheduleRetry(UUID operationId, int sequenceNo, Instant now, Instant nextAttemptAt, String failureCode, String failureMessage);

    boolean tryClaimCompensation(UUID operationId, int sequenceNo, UUID dispatchToken, Instant now, Instant stepDeadlineAt);

    boolean tryAcknowledgeStep(UUID operationId, int sequenceNo, Instant now, long appliedAggregateVersion, long appliedEpoch);

    Optional<DeleteMemberSagaStep> findActiveStep(UUID operationId);

    void markOperationManualReview(UUID operationId, String failureCode, String failureMessage, Instant now);
}
