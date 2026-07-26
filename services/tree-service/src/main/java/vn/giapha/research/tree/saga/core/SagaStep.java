package vn.giapha.research.tree.saga.core;

import java.util.Map;

public interface SagaStep {
    String name();
    SagaStepResult apply(SagaStepInput input);

    default SagaStepResult compensate(SagaStepInput input, SagaStepResult result) {
        return SagaStepResult.ok(Map.of());
    }
}
