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

/**
 * Adapter {@link DeleteTreeSagaGateway}: dùng outbox để stage các lệnh Saga
 * delete-tree. Mọi bản tin gửi đi sẽ chứa causationId = operationId để có thể
 * tái dựng chuỗi causality khi truy vết.
 */
@Component
public class OutboxDeleteTreeSagaGateway implements DeleteTreeSagaGateway {

    /** Bộ stage sự kiện vòng đời Saga. */
    private final OperationLifecycleOutboxStager lifecycle;

    /** ObjectMapper — chỉ dùng để ép serialize kiểm tra trước khi ghi outbox. */
    private final ObjectMapper json;

    /** Bộ nhớ context tạm cho envelope Saga gần nhất (causationId / traceparent). */
    private final DeleteTreeSagaCommandContext commandContext;

    /**
     * Khởi tạo gateway.
     *
     * @param lifecycle bộ stage vòng đời Saga
     * @param json      bộ mapper JSON
     * @param commandContext bộ nhớ context tạm
     */
    public OutboxDeleteTreeSagaGateway(OperationLifecycleOutboxStager lifecycle,
                                       ObjectMapper json,
                                       DeleteTreeSagaCommandContext commandContext) {
        this.lifecycle = lifecycle;
        this.json = json;
        this.commandContext = commandContext;
    }

    /**
     * Stage lệnh đầu tiên của Saga tới participant tương ứng. Đây là lệnh forward
     * (không phải compensation).
     *
     * @param state trạng thái Saga hiện tại
     * @param step  bước cần được gửi đi
     */
    @Override
    public void stageFirstStep(DeleteTreeSagaState state, DeleteTreeSagaStep step) {
        stageCommand(state, step.stepCode(), step.sequenceNo(), false);
    }

    /**
     * Stage lệnh compensation cho bước đã ACK trước đó. Mã bước compensation
     * được sinh từ {@link #compensationStepCode(String)}.
     *
     * @param state trạng thái Saga hiện tại
     * @param step  bước gốc cần bù
     */
    @Override
    public void stageCompensation(DeleteTreeSagaState state, DeleteTreeSagaStep step) {
        stageCommand(state, compensationStepCode(step.stepCode()), step.sequenceNo(), true);
    }

    /**
     * Stage sự kiện {@code OperationStarted} lên topic {@code operations.events.v1}.
     * Sự kiện này đánh dấu thời điểm Saga bắt đầu, dùng cho giao diện theo dõi.
     *
     * @param state trạng thái Saga khi vừa initiate
     */
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

    /**
     * Stage sự kiện thay đổi trạng thái Saga mà không kèm routing lỗi.
     *
     * @param state trạng thái Saga hiện tại
     */
    @Override
    public void stageOperationStateChanged(DeleteTreeSagaState state) {
        stageOperationStateChanged(state, null);
    }

    /**
     * Stage sự kiện {@code OperationStateChanged} kèm thông tin định tuyến lỗi
     * (ví dụ {@code MANUAL_REVIEW} hoặc {@code COMPENSATING}).
     *
     * @param state          trạng thái Saga
     * @param failureRouting mã định tuyến lỗi hoặc {@code null}
     */
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

    /**
     * Stage một lệnh Saga lên outbox. Lệnh có thể là forward hoặc compensation
     * tuỳ tham số {@code compensation}. Trước khi ghi, payload được serialize
     * thử để phát hiện sớm lỗi JSON.
     *
     * @param state        trạng thái Saga hiện tại
     * @param stepCode     mã bước (đã được map sang forward hoặc compensation)
     * @param sequenceNo   số thứ tự bước
     * @param compensation {@code true} nếu lệnh là compensation, ngược lại là forward
     */
    private void stageCommand(DeleteTreeSagaState state, String stepCode, int sequenceNo, boolean compensation) {
        // Tạo bản tin Saga chuẩn theo schema v1.
        Map<String, Object> env = new LinkedHashMap<>();
        String eventId = UUID.randomUUID().toString();
        env.put("eventId", eventId);
        env.put("correlationId", state.correlationId().toString());
        // CausationId is the immediately preceding message id (saga_common.proto)
        // not operationId; fall back to operationId only for the first forward command.
        String previousEventId = commandContext.lastEventId().orElse(state.operationId().toString());
        env.put("causationId", previousEventId);
        env.put("operationId", state.operationId().toString());
        env.put("treeId", state.treeId().toString());
        env.put("initiatingUserId", state.initiatingUserId().toString());
        env.put("schemaVersion", "v1");
        env.put("occurredAtEpochMs", Instant.now().toEpochMilli());
        env.put("traceparent", commandContext.traceparent().orElse(""));
        env.put("targetAggregateVersion", state.targetAggregateVersion());
        env.put("targetEpoch", state.targetEpoch());
        env.put("isCompensation", compensation);
        env.put("stepCode", stepCode);
        env.put("sequenceNo", sequenceNo);
        commandContext.recordEvent(eventId);
        // Thử serialize để chắc chắn payload hợp lệ trước khi xuất bản.
        try { json.writeValueAsString(env); } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize Saga envelope", e);
        }
        // Topic lệnh Saga là "tree.commands.v1"; eventType phân biệt command/compensation.
        lifecycle.stage(env, "tree.commands.v1",
                compensation ? "DeleteTreeSagaCompensation" : "DeleteTreeSagaCommand");
    }

    /**
     * Map mã bước forward sang mã bước compensation tương ứng. Với các mã không
     * có ánh xạ cụ thể, mặc định tiền tố {@code RESTORE_}.
     *
     * @param forwardCode mã bước forward
     * @return mã bước compensation tương ứng
     */
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