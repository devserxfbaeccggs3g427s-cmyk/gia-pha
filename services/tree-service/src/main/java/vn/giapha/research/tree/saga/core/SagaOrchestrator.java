package vn.giapha.research.tree.saga.core;

import java.util.List;
import java.util.Map;

public class SagaOrchestrator {
    private final String name;
    private final List<SagaStep> steps;
    private final SagaStateLog log;

    public SagaOrchestrator(String name, List<SagaStep> steps, SagaStateLog log) {
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("saga must have at least one step");
        }
        this.name = name;
        this.steps = List.copyOf(steps);
        this.log = log;
    }

    public SagaExecutionResult run(CommandContext context, Map<String, Object> payload) {
        String sagaId = log.start(name, context);
        log.transition(sagaId, SagaState.RUNNING);
        int applied = 0;
        for (SagaStep step : steps) {
            SagaStepInput input = SagaStepInput.of(step.name(), context, payload);
            SagaStepResult result = step.apply(input);
            log.recordStep(sagaId, step.name(), result);
            if (result.status() != SagaStepResult.Status.OK) {
                return compensate(sagaId, applied, context, payload);
            }
            applied++;
        }
        log.transition(sagaId, SagaState.SUCCEEDED);
        return new SagaExecutionResult(sagaId, SagaState.SUCCEEDED, applied, null);
    }

    private SagaExecutionResult compensate(String sagaId, int applied, CommandContext context,
            Map<String, Object> payload) {
        log.transition(sagaId, SagaState.COMPENSATING);
        for (int index = applied - 1; index >= 0; index--) {
            SagaStep step = steps.get(index);
            SagaStepResult result = step.compensate(
                    SagaStepInput.of(step.name(), context, payload),
                    log.lastResultFor(sagaId, step.name()));
            log.recordCompensation(sagaId, step.name(), result);
        }
        log.transition(sagaId, SagaState.COMPENSATED);
        return new SagaExecutionResult(sagaId, SagaState.COMPENSATED, applied, null);
    }
}
