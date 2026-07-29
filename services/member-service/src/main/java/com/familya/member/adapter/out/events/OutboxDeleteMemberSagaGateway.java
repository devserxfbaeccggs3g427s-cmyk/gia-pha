package com.familya.member.adapter.out.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.familya.member.application.port.out.DeleteMemberSagaGateway;
import com.familya.member.domain.model.DeleteMemberSagaState;
import com.familya.member.domain.model.DeleteMemberSagaStep;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stages every Saga command, compensation, and Operation lifecycle event on
 * the local transactional outbox. The actual Kafka publication is performed by
 * the shared platform outbox publisher. The payload schema mirrors the
 * {@code contracts/events/saga} protobuf definitions and the
 * {@code operations.events.v1} topic naming from the event catalog.
 */
@Component
public class OutboxDeleteMemberSagaGateway implements DeleteMemberSagaGateway {

    private final OperationLifecycleOutboxStager lifecycle;
    private final ObjectMapper json;

    public OutboxDeleteMemberSagaGateway(OperationLifecycleOutboxStager lifecycle, ObjectMapper json) {
        this.lifecycle = lifecycle;
        this.json = json;
    }

    @Override
    public void stageFirstStep(DeleteMemberSagaState state, DeleteMemberSagaStep step) {
        stageCommand(state, step.stepCode(), step.sequenceNo(), false);
    }

    @Override
    public void stageCompensation(DeleteMemberSagaState state, DeleteMemberSagaStep step) {
        stageCommand(state, compensationStepCode(step.stepCode()), step.sequenceNo(), true);
    }

    @Override
    public void stageOperationStarted(DeleteMemberSagaState state) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operationId", state.operationId().toString());
        payload.put("ownerService", "member-service");
        payload.put("sagaType", "delete-member");
        payload.put("treeId", state.treeId().toString());
        payload.put("initiatingUserId", state.initiatingUserId().toString());
        payload.put("startedAt", state.startedAt().toString());
        payload.put("targetAggregateVersion", state.targetAggregateVersion());
        payload.put("targetEpoch", state.targetEpoch());
        payload.put("deadlineAtEpochMs", state.deadlineAt().toEpochMilli());
        payload.put("schemaVersion", "v1");
        lifecycle.stage(payload, "operations.events.v1", "OperationStarted");
    }

    @Override
    public void stageOperationStateChanged(DeleteMemberSagaState state) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operationId", state.operationId().toString());
        payload.put("ownerService", "member-service");
        payload.put("state", state.state().name());
        payload.put("occurredAt", (state.finalizedAt() != null ? state.finalizedAt() : state.lastUpdatedAt()).toString());
        payload.put("ackAggregateVersion", state.targetAggregateVersion());
        payload.put("ackEpoch", state.targetEpoch());
        payload.put("failureCode", state.failureCode());
        payload.put("failureMessage", state.failureMessage());
        payload.put("schemaVersion", "v1");
        lifecycle.stage(payload, "operations.events.v1", "OperationStateChanged");
    }

    private void stageCommand(DeleteMemberSagaState state, String stepCode, int sequenceNo, boolean compensation) {
        Map<String, Object> env = buildEnvelope(state, stepCode, sequenceNo, compensation);
        lifecycle.stage(env, "member.commands.v1",
                compensation ? "DeleteMemberSagaCompensation" : "DeleteMemberSagaCommand");
    }

    private static String compensationStepCode(String forwardCode) {
        return switch (forwardCode) {
            case "DISABLE_RELATIONSHIPS"   -> "RESTORE_MEMBER_RELATIONSHIPS";
            case "DETACH_EVENT_REFERENCES" -> "RESTORE_MEMBER_EVENT_REFERENCES";
            case "DETACH_MEDIA_REFERENCES" -> "RESTORE_MEMBER_MEDIA_REFERENCES";
            default -> "RESTORE_" + forwardCode;
        };
    }

    private Map<String, Object> buildEnvelope(DeleteMemberSagaState state, String stepCode, int sequenceNo, boolean compensation) {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("eventId", UUID.randomUUID().toString());
        env.put("correlationId", state.correlationId().toString());
        env.put("causationId", state.operationId().toString());
        env.put("operationId", state.operationId().toString());
        env.put("treeId", state.treeId().toString());
        env.put("initiatingUserId", state.initiatingUserId().toString());
        env.put("schemaVersion", "v1");
        env.put("occurredAtEpochMs", Instant.now().toEpochMilli());
        env.put("traceparent", "");
        env.put("targetAggregateVersion", state.targetAggregateVersion());
        env.put("targetEpoch", state.targetEpoch());
        env.put("isCompensation", compensation);
        env.put("stepCode", stepCode);
        env.put("sequenceNo", sequenceNo);
        env.put("memberId", state.memberId().toString());
        try {
            json.writeValueAsString(env);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize Saga envelope", e);
        }
        return env;
    }
}