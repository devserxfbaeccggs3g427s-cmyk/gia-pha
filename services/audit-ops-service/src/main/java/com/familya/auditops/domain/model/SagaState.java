package com.familya.auditops.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Saga state machine envelope. Holds the current step name, whether
 * compensation is in flight, the versioned payload (opaque JSON), and
 * a version counter for optimistic concurrency.
 */
public final class SagaState {

    private final UUID operationId;
    private final String sagaType;
    private String currentStep;
    private boolean compensating;
    private int stepCount;
    private Instant lastTransitionAt;
    private final Map<String, Object> payload;
    private long version;

    public SagaState(UUID operationId,
                     String sagaType,
                     String currentStep,
                     boolean compensating,
                     int stepCount,
                     Instant lastTransitionAt,
                     Map<String, Object> payload,
                     long version) {
        this.operationId = Objects.requireNonNull(operationId);
        this.sagaType = Objects.requireNonNull(sagaType);
        this.currentStep = Objects.requireNonNull(currentStep);
        this.compensating = compensating;
        this.stepCount = stepCount;
        this.lastTransitionAt = Objects.requireNonNull(lastTransitionAt);
        this.payload = payload == null ? Map.of() : Map.copyOf(payload);
        this.version = version;
    }

    public UUID operationId() { return operationId; }
    public String sagaType() { return sagaType; }
    public String currentStep() { return currentStep; }
    public boolean compensating() { return compensating; }
    public int stepCount() { return stepCount; }
    public Instant lastTransitionAt() { return lastTransitionAt; }
    public Map<String, Object> payload() { return payload; }
    public long version() { return version; }

    public void transition(String nextStep, boolean compensating, Instant when) {
        this.currentStep = nextStep;
        this.compensating = compensating;
        this.lastTransitionAt = when;
        this.version = this.version + 1;
    }

    public void incrementStepCount() {
        this.stepCount = this.stepCount + 1;
    }
}