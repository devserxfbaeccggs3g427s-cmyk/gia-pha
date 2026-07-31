package com.familya.member.application.port.out;

import com.familya.member.domain.model.DeleteMemberSagaState;
import com.familya.member.domain.model.DeleteMemberSagaStep;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Hợp đồng (port) cho kho lưu trữ Saga xóa thành viên. Triển khai cụ thể thuộc tầng
 * adapter-out (xem {@code JdbcDeleteMemberSagaRepository}). Hợp đồng này ẩn giấu chi tiết
 * vật lý và cung cấp các thao tác cần thiết cho {@link com.familya.member.application.usecase.DeleteMemberSagaService}
 * và deadline scanner.
 */
public interface DeleteMemberSagaRepository {

    /**
     * Lưu (upsert) trạng thái Saga.
     * @param state trạng thái Saga cần lưu
     */
    void saveState(DeleteMemberSagaState state);

    /**
     * Tra cứu trạng thái Saga theo mã operationId.
     * @param operationId mã operationId
     * @return trạng thái Saga hoặc rỗng
     */
    Optional<DeleteMemberSagaState> findState(UUID operationId);

    /**
     * Lưu (upsert) nhiều bước Saga trong một batch.
     * @param steps danh sách bước cần lưu
     */
    void saveSteps(List<DeleteMemberSagaStep> steps);

    /**
     * Liệt kê các bước của một Saga theo thứ tự sequenceNo.
     * @param operationId mã operationId
     * @return danh sách bước
     */
    List<DeleteMemberSagaStep> listSteps(UUID operationId);

    /**
     * Cập nhật một bước Saga.
     * @param step bước cần cập nhật
     */
    void updateStep(DeleteMemberSagaStep step);

    /**
     * Lưu snapshot bù trừ cho một participant.
     * @param operationId       mã operationId
     * @param participantService tên dịch vụ tham gia
     * @param snapshotJson      payload snapshot dạng JSON
     */
    void saveCompensationSnapshot(UUID operationId, String participantService, String snapshotJson);

    /**
     * Tải snapshot bù trừ đã lưu cho một participant.
     * @param operationId       mã operationId
     * @param participantService tên dịch vụ tham gia
     * @return payload snapshot hoặc rỗng
     */
    Optional<String> loadCompensationSnapshot(UUID operationId, String participantService);

    /**
     * Liệt kê các Saga ở trạng thái không cuối đã quá deadline.
     * @return danh sách các trạng thái Saga quá hạn
     */
    List<DeleteMemberSagaState> listDispatchedPastDeadline();

    /**
     * Liệt kê các bước đang FAILED có thể thử lại (còn lượt và đã tới thời điểm retry).
     * @param now   thời điểm hiện tại
     * @param limit số lượng tối đa
     * @return danh sách bước có thể retry
     */
    List<DeleteMemberSagaStep> listRetryableSteps(Instant now, int limit);

    /**
     * Liệt kê các bước DISPATCHED đã quá deadline riêng.
     * @param now   thời điểm hiện tại
     * @param limit số lượng tối đa
     * @return danh sách bước timeout
     */
    List<DeleteMemberSagaStep> listTimedOutSteps(Instant now, int limit);

    /**
     * Cố gắng giành quyền dispatch cho một bước Saga.
     * @return {@code true} nếu giành được, {@code false} nếu bước đã được dispatch
     */
    boolean tryClaimDispatch(UUID operationId, int sequenceNo, UUID dispatchToken, Instant now, Instant stepDeadlineAt);

    /**
     * Giải phóng token và lên lịch retry cho bước Saga.
     * @return {@code true} nếu cập nhật thành công
     */
    boolean releaseOrScheduleRetry(UUID operationId, int sequenceNo, Instant now, Instant nextAttemptAt, String failureCode, String failureMessage);

    /**
     * Cố gắng giành quyền dispatch cho compensation.
     * @return {@code true} nếu giành được
     */
    boolean tryClaimCompensation(UUID operationId, int sequenceNo, UUID dispatchToken, Instant now, Instant stepDeadlineAt);

    /**
     * Đánh dấu một bước Saga là ACK.
     * @return {@code true} nếu cập nhật thành công
     */
    boolean tryAcknowledgeStep(UUID operationId, int sequenceNo, Instant now, long appliedAggregateVersion, long appliedEpoch);

    /**
     * Tìm bước Saga đang hoạt động (chưa ACK/COMPENSATED/DEAD_LETTERED) đầu tiên.
     * @return bước đang hoạt động hoặc rỗng
     */
    Optional<DeleteMemberSagaStep> findActiveStep(UUID operationId);

    /**
     * Đánh dấu operation ở trạng thái MANUAL_REVIEW.
     * @param operationId    mã operationId
     * @param failureCode    mã lỗi
     * @param failureMessage mô tả lỗi
     * @param now            thời điểm đánh dấu
     */
    void markOperationManualReview(UUID operationId, String failureCode, String failureMessage, Instant now);
}
