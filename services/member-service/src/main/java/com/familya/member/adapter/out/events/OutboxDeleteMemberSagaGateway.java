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
/**
 * Triển khai {@link DeleteMemberSagaGateway} dựa trên outbox. Mỗi lệnh Saga, compensation,
 * và sự kiện vòng đời operation được stage lên outbox cục bộ; platform publisher sẽ phát
 * chúng lên Kafka. Lược đồ payload phản ánh các định nghĩa protobuf trong
 * {@code contracts/events/saga} và quy ước đặt tên topic {@code operations.events.v1} theo event catalog.
 *
 * <p>Bean {@code @Component} thuộc tầng adapter-out trong kiến trúc Hexagonal.
 */
@Component
public class OutboxDeleteMemberSagaGateway implements DeleteMemberSagaGateway {

    private final OperationLifecycleOutboxStager lifecycle;
    private final ObjectMapper json;
    private final DeleteMemberSagaCommandContext commandContext;

    /**
     * Khởi tạo gateway với stager vòng đời và mapper JSON.
     *
     * @param lifecycle stager vòng đời operation
     * @param json      mapper JSON (hiện chỉ dùng để xác thực khả năng serialize của envelope)
     * @param commandContext bộ nhớ context tạm cho envelope Saga gần nhất (causationId)
     */
    public OutboxDeleteMemberSagaGateway(OperationLifecycleOutboxStager lifecycle,
                                        ObjectMapper json,
                                        DeleteMemberSagaCommandContext commandContext) {
        this.lifecycle = lifecycle;
        this.json = json;
        this.commandContext = commandContext;
    }

    /**
     * Stage lệnh forward đầu tiên của Saga lên outbox.
     *
     * @param state trạng thái Saga hiện tại
     * @param step  bước cần stage
     */
    @Override
    public void stageFirstStep(DeleteMemberSagaState state, DeleteMemberSagaStep step) {
        stageCommand(state, step.stepCode(), step.sequenceNo(), false);
    }

    /**
     * Stage lệnh compensation tương ứng với một bước Saga lên outbox.
     *
     * @param state trạng thái Saga
     * @param step  bước cần compensate
     */
    @Override
    public void stageCompensation(DeleteMemberSagaState state, DeleteMemberSagaStep step) {
        stageCommand(state, compensationStepCode(step.stepCode()), step.sequenceNo(), true);
    }

    /**
     * Stage sự kiện {@code OperationStarted} lên outbox.
     *
     * @param state trạng thái Saga hiện tại
     */
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

    /** Stage sự kiện OperationStateChanged không kèm thông tin định tuyến lỗi. */
    @Override
    public void stageOperationStateChanged(DeleteMemberSagaState state) {
        stageOperationStateChanged(state, null);
    }

    /**
     * Stage sự kiện OperationStateChanged với khóa định tuyến lỗi (failureRouting).
     *
     * @param state          trạng thái Saga
     * @param failureRouting mã định tuyến lỗi (ví dụ: {@code COMPENSATING}, {@code MANUAL_REVIEW})
     */
    @Override
    public void stageOperationStateChanged(DeleteMemberSagaState state, String failureRouting) {
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
        if (failureRouting != null) {
            payload.put("failureRouting", failureRouting);
        }
        lifecycle.stage(payload, "operations.events.v1", "OperationStateChanged");
    }

    /**
     * Stage một lệnh Saga (forward hoặc compensation) lên outbox.
     *
     * @param state        trạng thái Saga
     * @param stepCode     mã bước
     * @param sequenceNo   số thứ tự bước
     * @param compensation {@code true} nếu là compensation, {@code false} nếu là forward
     */
    private void stageCommand(DeleteMemberSagaState state, String stepCode, int sequenceNo, boolean compensation) {
        Map<String, Object> env = buildEnvelope(state, stepCode, sequenceNo, compensation);
        lifecycle.stage(env, "member.commands.v1",
                compensation ? "DeleteMemberSagaCompensation" : "DeleteMemberSagaCommand");
    }

    /**
     * Ánh xạ mã bước forward sang mã compensation tương ứng. Mặc định thêm tiền tố {@code RESTORE_}.
     *
     * @param forwardCode mã bước forward
     * @return mã bước compensation
     */
    private static String compensationStepCode(String forwardCode) {
        return switch (forwardCode) {
            case "DISABLE_RELATIONSHIPS"   -> "RESTORE_MEMBER_RELATIONSHIPS";
            case "DETACH_EVENT_REFERENCES" -> "RESTORE_MEMBER_EVENT_REFERENCES";
            case "DETACH_MEDIA_REFERENCES" -> "RESTORE_MEMBER_MEDIA_REFERENCES";
            default -> "RESTORE_" + forwardCode;
        };
    }

    /**
     * Tạo phong bì lệnh Saga với đầy đủ metadata (eventId, correlation, causation, v.v.).
     * Việc gọi {@code writeValueAsString} chỉ nhằm mục đích xác thực khả năng serialize;
     * nội dung thực tế ghi vào outbox là chính map {@code env}.
     *
     * @param state        trạng thái Saga
     * @param stepCode     mã bước
     * @param sequenceNo   số thứ tự bước
     * @param compensation cờ phân biệt forward/compensation
     * @return bản đồ chứa envelope lệnh
     */
    private Map<String, Object> buildEnvelope(DeleteMemberSagaState state, String stepCode, int sequenceNo, boolean compensation) {
        Map<String, Object> env = new LinkedHashMap<>();
        String eventId = UUID.randomUUID().toString();
        env.put("eventId", eventId);
        env.put("correlationId", state.correlationId().toString());
        // CausationId is the immediately preceding message id (not operationId) per
        // saga_common.proto. For the first forward command it falls back to the
        // operationId (which the relay will see as the originating event of the Saga).
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
        env.put("memberId", state.memberId().toString());
        commandContext.recordEvent(eventId);
        try {
            json.writeValueAsString(env);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize Saga envelope", e);
        }
        return env;
    }
}