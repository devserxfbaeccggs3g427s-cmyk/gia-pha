package com.familya.member.application.port.out;

import com.familya.member.domain.model.DeleteMemberSagaState;
import com.familya.member.domain.model.DeleteMemberSagaStep;

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
}