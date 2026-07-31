package com.familya.member.application.port.out;

import com.familya.member.domain.model.DeleteMemberSagaState;
import com.familya.member.domain.model.DeleteMemberSagaStep;

import java.util.UUID;

/**
 * Side-effects of the delete-member Saga: Saga command publication on Kafka,
 * the OperationStarted/StateChanged lifecycle events consumed by Audit Ops,
 * and the participant reply publication. Each method must be invoked from a
 * transaction whose outbox row is committed atomically with Saga state.
 */
public interface DeleteMemberSagaGateway {

    /**
     * Stage lệnh forward đầu tiên của Saga lên outbox trong cùng transaction với state Saga.
     *
     * @param state trạng thái Saga
     * @param step  bước cần stage
     */
    void stageFirstStep(DeleteMemberSagaState state, DeleteMemberSagaStep step);

    /**
     * Stage lệnh bù trừ (compensation) cho một bước Saga lên outbox.
     *
     * @param state trạng thái Saga
     * @param step  bước cần bù trừ
     */
    void stageCompensation(DeleteMemberSagaState state, DeleteMemberSagaStep step);

    /**
     * Stage sự kiện {@code OperationStarted} để Audit Ops tiêu thụ.
     *
     * @param state trạng thái Saga
     */
    void stageOperationStarted(DeleteMemberSagaState state);

    /**
     * Stage sự kiện {@code OperationStateChanged} để Audit Ops tiêu thụ.
     *
     * @param state trạng thái Saga
     */
    void stageOperationStateChanged(DeleteMemberSagaState state);

    /**
     * Stage {@code OperationStateChanged} với khóa định tuyến lỗi rõ ràng cho projection của Audit Ops.
     *
     * @param state          trạng thái Saga
     * @param failureRouting mã định tuyến lỗi (ví dụ: {@code COMPENSATING}, {@code MANUAL_REVIEW})
     */
    void stageOperationStateChanged(DeleteMemberSagaState state, String failureRouting);
}