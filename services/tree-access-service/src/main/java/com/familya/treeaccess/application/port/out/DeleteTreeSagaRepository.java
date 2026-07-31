package com.familya.treeaccess.application.port.out;

import com.familya.treeaccess.domain.model.DeleteTreeSagaState;
import com.familya.treeaccess.domain.model.DeleteTreeSagaStep;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeleteTreeSagaRepository {

    void saveState(DeleteTreeSagaState state);

    Optional<DeleteTreeSagaState> findState(UUID operationId);

    void saveSteps(List<DeleteTreeSagaStep> steps);

    List<DeleteTreeSagaStep> listSteps(UUID operationId);

    void updateStep(DeleteTreeSagaStep step);

    void saveCompensationSnapshot(UUID operationId, String participantService, String snapshotJson);

    Optional<String> loadCompensationSnapshot(UUID operationId, String participantService);

    List<DeleteTreeSagaState> listActivePastDeadline();

    List<DeleteTreeSagaStep> listRetryableSteps(Instant now, int limit);

    List<DeleteTreeSagaStep> listTimedOutSteps(Instant now, int limit);

    boolean tryClaimDispatch(UUID operationId, int sequenceNo, UUID dispatchToken, Instant now, Instant stepDeadlineAt);

    boolean releaseOrScheduleRetry(UUID operationId, int sequenceNo, Instant now, Instant nextAttemptAt, String failureCode, String failureMessage);

    boolean tryClaimCompensation(UUID operationId, int sequenceNo, UUID dispatchToken, Instant now, Instant stepDeadlineAt);

    boolean tryAcknowledgeStep(UUID operationId, int sequenceNo, Instant now, long appliedAggregateVersion, long appliedEpoch);

    Optional<DeleteTreeSagaStep> findActiveStep(UUID operationId);

    void markOperationManualReview(UUID operationId, String failureCode, String failureMessage, Instant now);
}
