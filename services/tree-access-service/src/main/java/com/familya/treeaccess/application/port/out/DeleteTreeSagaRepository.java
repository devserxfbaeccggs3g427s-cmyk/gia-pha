package com.familya.treeaccess.application.port.out;

import com.familya.treeaccess.domain.model.DeleteTreeSagaState;
import com.familya.treeaccess.domain.model.DeleteTreeSagaStep;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port kho lưu trữ cho Saga delete-tree. Bao gồm các thao tác CRUD cơ bản
 * và các "claim/transition" có điều kiện giúp nhiều worker xử lý đồng thời
 * mà vẫn bảo toàn tính nguyên tử ở mức hàng.
 */
public interface DeleteTreeSagaRepository {

    /**
     * Lưu hoặc cập nhật trạng thái Saga.
     *
     * @param state trạng thái cần lưu
     */
    void saveState(DeleteTreeSagaState state);

    /**
     * Tìm trạng thái Saga theo mã thao tác.
     *
     * @param operationId mã thao tác
     * @return {@link Optional} chứa trạng thái nếu tồn tại
     */
    Optional<DeleteTreeSagaState> findState(UUID operationId);

    /**
     * Ghi nhiều bước Saga (upsert).
     *
     * @param steps danh sách bước
     */
    void saveSteps(List<DeleteTreeSagaStep> steps);

    /**
     * Lấy tất cả bước của một Saga theo thứ tự.
     *
     * @param operationId mã thao tác
     * @return danh sách bước
     */
    List<DeleteTreeSagaStep> listSteps(UUID operationId);

    /**
     * Cập nhật một bước duy nhất.
     *
     * @param step bước cần cập nhật
     */
    void updateStep(DeleteTreeSagaStep step);

    /**
     * Lưu snapshot dữ liệu bù compensation.
     *
     * @param operationId       mã thao tác
     * @param participantService tên service tham gia
     * @param snapshotJson      JSON dữ liệu cần bù
     */
    void saveCompensationSnapshot(UUID operationId, String participantService, String snapshotJson);

    /**
     * Nạp snapshot compensation đã lưu.
     *
     * @param operationId       mã thao tác
     * @param participantService tên service tham gia
     * @return chuỗi JSON nếu có
     */
    Optional<String> loadCompensationSnapshot(UUID operationId, String participantService);

    /**
     * Danh sách các Saga đang hoạt động nhưng đã quá deadline.
     *
     * @return danh sách trạng thái Saga quá hạn
     */
    List<DeleteTreeSagaState> listActivePastDeadline();

    /**
     * Danh sách các bước có thể retry (đã tới {@code next_attempt_at}).
     *
     * @param now   thời điểm hiện tại
     * @param limit số bản ghi tối đa
     * @return danh sách bước
     */
    List<DeleteTreeSagaStep> listRetryableSteps(Instant now, int limit);

    /**
     * Danh sách các bước đã gửi đi nhưng quá hạn ACK.
     *
     * @param now   thời điểm hiện tại
     * @param limit số bản ghi tối đa
     * @return danh sách bước
     */
    List<DeleteTreeSagaStep> listTimedOutSteps(Instant now, int limit);

    /**
     * Cố gắng giành quyền dispatch cho bước ở trạng thái PENDING/FAILED.
     *
     * @param operationId    mã thao tác
     * @param sequenceNo     số thứ tự
     * @param dispatchToken  token claim
     * @param now            thời điểm claim
     * @param stepDeadlineAt deadline step
     * @return {@code true} nếu claim thành công
     */
    boolean tryClaimDispatch(UUID operationId, int sequenceNo, UUID dispatchToken, Instant now, Instant stepDeadlineAt);

    /**
     * Giải phóng claim và lên lịch retry.
     *
     * @param operationId    mã thao tác
     * @param sequenceNo     số thứ tự
     * @param now            thời điểm hiện tại
     * @param nextAttemptAt  thời điểm retry kế tiếp
     * @param failureCode    mã lỗi
     * @param failureMessage thông điệp
     * @return {@code true} nếu cập nhật thành công
     */
    boolean releaseOrScheduleRetry(UUID operationId, int sequenceNo, Instant now, Instant nextAttemptAt, String failureCode, String failureMessage);

    /**
     * Cố gắng giành quyền dispatch compensation cho bước đã ACK.
     *
     * @param operationId    mã thao tác
     * @param sequenceNo     số thứ tự
     * @param dispatchToken  token claim
     * @param now            thời điểm claim
     * @param stepDeadlineAt deadline compensation
     * @return {@code true} nếu claim thành công
     */
    boolean tryClaimCompensation(UUID operationId, int sequenceNo, UUID dispatchToken, Instant now, Instant stepDeadlineAt);

    /**
     * Đánh dấu một bước đã nhận được ACK với revision/epoch đã áp dụng.
     *
     * @param operationId           mã thao tác
     * @param sequenceNo            số thứ tự
     * @param now                   thời điểm hiện tại
     * @param appliedAggregateVersion phiên bản đã áp dụng
     * @param appliedEpoch          epoch đã áp dụng
     * @return {@code true} nếu cập nhật thành công
     */
    boolean tryAcknowledgeStep(UUID operationId, int sequenceNo, Instant now, long appliedAggregateVersion, long appliedEpoch);

    /**
     * Tìm bước đang hoạt động của một Saga.
     *
     * @param operationId mã thao tác
     * @return bước đang hoạt động nếu có
     */
    Optional<DeleteTreeSagaStep> findActiveStep(UUID operationId);

    /**
     * Đánh dấu Saga vào trạng thái {@code MANUAL_REVIEW}.
     *
     * @param operationId    mã thao tác
     * @param failureCode    mã lỗi
     * @param failureMessage thông điệp
     * @param now            thời điểm cập nhật
     */
    void markOperationManualReview(UUID operationId, String failureCode, String failureMessage, Instant now);
}
