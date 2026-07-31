package com.familya.treeaccess.adapter.out.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.familya.treeaccess.application.port.out.DeleteTreeSagaGateway;
import com.familya.treeaccess.domain.model.DeleteTreeSagaState;
import com.familya.treeaccess.domain.model.DeleteTreeSagaStep;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class OutboxDeleteTreeSagaGateway implements DeleteTreeSagaGateway {

    private final OperationLifecycleOutboxStager lifecycle;
    private final ObjectMapper json;

    public OutboxDeleteTreeSagaGateway(OperationLifecycleOutboxStager lifecycle, ObjectMapper json) {
        this.lifecycle = lifecycle;
        this.json = json;
    }

    @Override
    public void stageFirstStep(DeleteTreeSagaState state, DeleteTreeSagaStep step) {
        stageCommand(state, step.stepCode(), step.sequenceNo(), false);
    }

    @Override
    public void stageCompensation(DeleteTreeSagaState state, DeleteTreeSagaStep step) {
        stageCommand(state, compensationStepCode(step.stepCode()), step.sequenceNo(), true);
    }

    @Override
    public void stageOperationStarted(DeleteTreeSagaState state) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operationId", state.operationId().toString());
        payload.put("ownerService", "tree-access-service");
        payload.put("sagaType", "delete-tree");
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
    public void stageOperationStateChanged(DeleteTreeSagaState state) {
        stageOperationStateChanged(state, null);
    }

    @Override
    public void stageOperationStateChanged(DeleteTreeSagaState state, String failureRouting) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operationId", state.operationId().toString());
        payload.put("ownerService", "tree-access-service");
        payload.put("state", state.state().name());
        payload.put("occurredAt", (state.finalizedAt() != null ? state.finalizedAt() : state.lastUpdatedAt()).toString());
        payload.put("ackAggregateVersion", state.targetAggregateVersion());
        payload.put("ackEpoch", state.targetEpoch());
        payload.put("failureCode", state.failureCode());
        payload.put("failureMessage", state.failureMessage());
        payload.put("schemaVersion", "v1");
        if (failureRouting != null) {
            payload.put("failureRouting", failureRouting);
        }
        lifecycle.stage(payload, "operations.events.v1", "OperationStateChanged");
    }

    private void stageCommand(DeleteTreeSagaState state, String stepCode, int sequenceNo, boolean compensation) {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("eventId", UUID.randomUUID().toString());
        env.put("correlationId", state.correlationId().toString());
        env.put("causationId", state.operationId().toString());
        env.put("operationId", state.operationId().toString());
        env.put("treeId", state.treeId().toString());
        env.put("initiatingUserId", state.initiatingUserId().toString());
        env.put("schemaVersion", "v1");
        env.put("occurredAtEpochMs", Instant.now().toEpochMilli());
        env.put("targetAggregateVersion", state.targetAggregateVersion());
        env.put("targetEpoch", state.targetEpoch());
        env.put("isCompensation", compensation);
        env.put("stepCode", stepCode);
        env.put("sequenceNo", sequenceNo);
        try { json.writeValueAsString(env); } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize Saga envelope", e);
        }
        lifecycle.stage(env, "tree.commands.v1",
                compensation ? "DeleteTreeSagaCompensation" : "DeleteTreeSagaCommand");
    }

    private static String compensationStepCode(String forwardCode) {
        return switch (forwardCode) {
            case "PURGE_MEMBER_TREE"         -> "RESTORE_MEMBER_TREE";
            case "PURGE_RELATIONSHIP_TREE"   -> "RESTORE_RELATIONSHIP_TREE";
            case "PURGE_EVENT_TREE"          -> "RESTORE_EVENT_TREE";
            case "PURGE_MEDIA_METADATA_TREE" -> "RESTORE_MEDIA_METADATA_TREE";
            case "REVOKE_SHARING_TREE"       -> "RESTORE_SHARING_TREE";
            case "PURGE_SEARCH_TREE"         -> "RESTORE_SEARCH_TREE";
            case "TOMBSTONE_TREE"            -> "RESTORE_TREE";
            default -> "RESTORE_" + forwardCode;
        };
    }
}