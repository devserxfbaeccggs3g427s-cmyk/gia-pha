package com.familya.treeaccess.application.port.out;

import com.familya.treeaccess.domain.model.DeleteTreeSagaState;
import com.familya.treeaccess.domain.model.DeleteTreeSagaStep;

/**
 * Port ra ngoài cho phép use-case Saga tương tác với các hệ thống message
 * bên ngoài (outbox). Mọi phương thức đều chỉ stage bản tin; việc phát hành
 * thực sự được thực hiện bởi một publisher chạy nền.
 */
public interface DeleteTreeSagaGateway {

    /**
     * Stage bước đầu tiên (forward) của Saga cho tham gia viên tương ứng.
     *
     * @param state trạng thái Saga hiện tại
     * @param step  bước cần gửi đi
     */
    void stageFirstStep(DeleteTreeSagaState state, DeleteTreeSagaStep step);

    /**
     * Stage lệnh compensation cho một bước đã ACK trước đó.
     *
     * @param state trạng thái Saga hiện tại
     * @param step  bước gốc cần bù
     */
    void stageCompensation(DeleteTreeSagaState state, DeleteTreeSagaStep step);

    /**
     * Stage sự kiện {@code OperationStarted} lên topic vòng đời thao tác.
     *
     * @param state trạng thái Saga khi mới khởi tạo
     */
    void stageOperationStarted(DeleteTreeSagaState state);

    /**
     * Stage sự kiện {@code OperationStateChanged} không kèm routing lỗi.
     *
     * @param state trạng thái Saga hiện tại
     */
    void stageOperationStateChanged(DeleteTreeSagaState state);

    /**
     * Stage sự kiện {@code OperationStateChanged} kèm thông tin định tuyến lỗi.
     *
     * @param state          trạng thái Saga
     * @param failureRouting mã định tuyến lỗi hoặc {@code null}
     */
    void stageOperationStateChanged(DeleteTreeSagaState state, String failureRouting);
}