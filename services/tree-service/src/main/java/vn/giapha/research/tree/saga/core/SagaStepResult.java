package vn.giapha.research.tree.saga.core;

import java.util.Map;

public record SagaStepResult(Status status, Map<String, Object> output, String failureReason) {
    public enum Status { OK, RETRYABLE_FAILURE, PERMANENT_FAILURE }

    public static SagaStepResult ok(Map<String, Object> output) {
        return new SagaStepResult(Status.OK, output, null);
    }
}
