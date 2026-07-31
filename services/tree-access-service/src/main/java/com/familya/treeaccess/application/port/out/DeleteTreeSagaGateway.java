package com.familya.treeaccess.application.port.out;

import com.familya.treeaccess.domain.model.DeleteTreeSagaState;
import com.familya.treeaccess.domain.model.DeleteTreeSagaStep;

public interface DeleteTreeSagaGateway {

    void stageFirstStep(DeleteTreeSagaState state, DeleteTreeSagaStep step);

    void stageCompensation(DeleteTreeSagaState state, DeleteTreeSagaStep step);

    void stageOperationStarted(DeleteTreeSagaState state);

    void stageOperationStateChanged(DeleteTreeSagaState state);

    void stageOperationStateChanged(DeleteTreeSagaState state, String failureRouting);
}