package vn.giapha.research.tree.saga.core;

public record SagaExecutionResult(String sagaId, SagaState finalState, int appliedSteps,
                                  SagaStepResult lastResult) {}
