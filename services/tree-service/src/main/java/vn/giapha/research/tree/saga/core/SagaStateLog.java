package vn.giapha.research.tree.saga.core;

public interface SagaStateLog {
    String start(String sagaName, CommandContext context);
    void transition(String sagaId, SagaState state);
    void recordStep(String sagaId, String stepName, SagaStepResult result);
    void recordCompensation(String sagaId, String stepName, SagaStepResult result);
    SagaStepResult lastResultFor(String sagaId, String stepName);
}
