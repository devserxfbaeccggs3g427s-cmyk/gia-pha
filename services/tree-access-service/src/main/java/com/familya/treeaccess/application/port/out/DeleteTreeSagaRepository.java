package com.familya.treeaccess.application.port.out;

import com.familya.treeaccess.domain.model.DeleteTreeSagaState;
import com.familya.treeaccess.domain.model.DeleteTreeSagaStep;

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
}