package vn.giapha.research.tree.saga.core;

import java.util.Map;

public record SagaStepInput(String stepName, CommandContext context, Map<String, Object> payload) {
    public static SagaStepInput of(String name, CommandContext context, Map<String, Object> payload) {
        return new SagaStepInput(name, context, payload);
    }
}
