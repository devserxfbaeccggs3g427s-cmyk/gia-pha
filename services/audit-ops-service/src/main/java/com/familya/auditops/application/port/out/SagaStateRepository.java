/**
 * Port bền vững cho máy trạng thái Saga.
 *
 * <p>Orchestrator sử dụng port này để đọc và ghi trạng thái Saga
 * bền vững. Mọi thao tác ghi commit trong cùng transaction cục bộ
 * với transition operation và audit append.</p>
 */
package com.familya.auditops.application.port.out;

import com.familya.auditops.domain.model.SagaState;
import com.familya.auditops.domain.model.SagaStep;
import com.familya.auditops.domain.model.StepStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Interface quản lý envelope trạng thái Saga và các step.
 */
public interface SagaStateRepository {

    /**
     * Tìm envelope trạng thái Saga của operation.
     *
     * @param operationId id operation
     * @return {@link Optional} chứa {@link SagaState} nếu tồn tại
     */
    Optional<SagaState> findState(UUID operationId);

    /**
     * Lưu envelope trạng thái Saga (upsert).
     *
     * @param state trạng thái cần lưu
     * @return trạng thái đã lưu
     */
    SagaState saveState(SagaState state);

    /**
     * Liệt kê step của một operation.
     *
     * @param operationId id operation
     * @return danh sách {@link SagaStep}
     */
    List<SagaStep> listSteps(UUID operationId);

    /**
     * Lưu step Saga (upsert).
     *
     * @param step step cần lưu
     * @return step đã lưu
     */
    SagaStep saveStep(SagaStep step);

    /**
     * Chuyển trạng thái step bằng optimistic concurrency.
     *
     * @param operationId       id operation
     * @param participantService tên participant
     * @param stepName          tên step
     * @param next              trạng thái mới
     * @param errorCode         mã lỗi (nếu có)
     * @param errorMessage      thông điệp lỗi (nếu có)
     * @param when              thời điểm áp dụng
     * @return step đã chuyển trạng thái
     * @throws com.familya.platform.error.OptimisticConcurrencyException nếu version không khớp
     */
    SagaStep transitionStep(UUID operationId,
                            String participantService,
                            String stepName,
                            StepStatus next,
                            String errorCode,
                            String errorMessage,
                            java.time.Instant when);

    /**
     * Đưa step vào dead-letter table khi đã hết retry.
     *
     * <p>Orchestrator <b>BẮT BUỘC</b> theo sau thao tác này bằng một
     * transition operation sang {@code MANUAL_REVIEW} (Task 13 / ADR-003).</p>
     *
     * @param operationId       id operation
     * @param participantService tên participant
     * @param stepName          tên step
     * @param errorCode         mã lỗi
     * @param errorMessage      thông điệp lỗi
     * @param payload           payload liên quan
     * @param when              thời điểm quarantine
     */
    void deadLetterStep(UUID operationId,
                        String participantService,
                        String stepName,
                        String errorCode,
                        String errorMessage,
                        java.util.Map<String, Object> payload,
                        java.time.Instant when);

    /**
     * Đếm số step của một operation ở một trạng thái.
     *
     * <p>Dùng cho quy tắc target-revision completion.</p>
     *
     * @param operationId id operation
     * @param status      trạng thái cần đếm
     * @return số step khớp
     */
    long countByOperationAndStatus(UUID operationId, StepStatus status);
}