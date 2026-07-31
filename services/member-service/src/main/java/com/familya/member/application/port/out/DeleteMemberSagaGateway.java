package com.familya.member.application.port.out;

import com.familya.member.domain.model.DeleteMemberSagaState;
import com.familya.member.domain.model.DeleteMemberSagaStep;

import java.util.UUID;

/**
 * Side-effects of the delete-member Saga: Saga command publication on Kafka,
 * the OperationStarted/StateChanged lifecycle events consumed by Audit Ops,
 * and the participant reply publication. Each method must be invoked from a
 * transaction whose outbox row is committed atomically with Saga state.
 */
public interface DeleteMemberSagaGateway {

    /** Stage the first Saga command on the outbox in the same transaction. */
    void stageFirstStep(DeleteMemberSagaState state, DeleteMemberSagaStep step);

    /** Stage a compensation command for one step on the outbox. */
    void stageCompensation(DeleteMemberSagaState state, DeleteMemberSagaStep step);

    /** Stage the OperationStarted event consumed by Audit Ops. */
    void stageOperationStarted(DeleteMemberSagaState state);

    /** Stage an OperationStateChanged event consumed by Audit Ops. */
    void stageOperationStateChanged(DeleteMemberSagaState state);

    /** Stage OperationStateChanged with an explicit failureRouting tag for Audit Ops projection. */
    void stageOperationStateChanged(DeleteMemberSagaState state, String failureRouting);
}