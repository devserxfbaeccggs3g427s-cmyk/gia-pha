/**
 * Command ghi nhận phản hồi của participant cho một Saga step.
 *
 * <p>Được phát ra bởi consumer {@code SagaReplyListener}. Phản hồi
 * mang theo {@code ackedRevision} và {@code ackedEpoch} để orchestrator
 * có thể áp dụng quy tắc target-revision completion (Task 13).</p>
 */
package com.familya.auditops.application.port.in;

import java.util.Objects;
import java.util.UUID;

/**
 * Command đóng gói phản hồi của participant.
 *
 * <p>Các trường operationId, participantService, stepName và outcome
 * là bắt buộc; các trường còn lại có thể null tuỳ trường hợp.</p>
 */
public final class RecordParticipantReplyCommand {

    /**
     * Kết quả của step.
     * <ul>
     *   <li>{@link #ACKED}: participant đã xử lý thành công.</li>
     *   <li>{@link #FAILED}: participant thất bại; orchestrator sẽ retry
     *       hoặc chuyển sang dead-letter tuỳ policy.</li>
     *   <li>{@link #COMPENSATED}: participant đã thực hiện compensation.</li>
     *   <li>{@link #DEAD_LETTERED}: participant không thể xử lý và đã
     *       chuyển message vào dead-letter.</li>
     * </ul>
     */
    public enum Outcome { ACKED, FAILED, COMPENSATED, DEAD_LETTERED }

    /** UUID của operation. */
    private final UUID operationId;
    /** Tên bounded context participant. */
    private final String participantService;
    /** Tên step trong Saga. */
    private final String stepName;
    /** Kết quả thực thi step. */
    private final Outcome outcome;
    /** Revision mà participant đã ghi nhận (nếu có). */
    private final Long ackedRevision;
    /** Epoch mà participant đã ghi nhận (nếu có). */
    private final Long ackedEpoch;
    /** Version mà orchestrator đã gửi cho participant. */
    private final Long expectedVersion;
    /** Mã lỗi (nếu thất bại). */
    private final String errorCode;
    /** Thông điệp lỗi (nếu thất bại). */
    private final String errorMessage;
    /** Correlation id lấy từ header (nếu có). */
    private final String correlationId;
    /** Causation id lấy từ header (nếu có). */
    private final String causationId;

    /**
     * Khởi tạo command.
     *
     * @param operationId       UUID operation
     * @param participantService tên participant
     * @param stepName          tên step
     * @param outcome           kết quả
     * @param ackedRevision     revision được ack (có thể null)
     * @param ackedEpoch        epoch được ack (có thể null)
     * @param expectedVersion   version mong đợi
     * @param errorCode         mã lỗi
     * @param errorMessage      thông điệp lỗi
     * @param correlationId     correlation id
     * @param causationId       causation id
     */
    public RecordParticipantReplyCommand(UUID operationId,
                                         String participantService,
                                         String stepName,
                                         Outcome outcome,
                                         Long ackedRevision,
                                         Long ackedEpoch,
                                         Long expectedVersion,
                                         String errorCode,
                                         String errorMessage,
                                         String correlationId,
                                         String causationId) {
        this.operationId = Objects.requireNonNull(operationId);
        this.participantService = Objects.requireNonNull(participantService);
        this.stepName = Objects.requireNonNull(stepName);
        this.outcome = Objects.requireNonNull(outcome);
        this.ackedRevision = ackedRevision;
        this.ackedEpoch = ackedEpoch;
        this.expectedVersion = expectedVersion;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.correlationId = correlationId;
        this.causationId = causationId;
    }

    /** @return UUID operation. */
    public UUID operationId() { return operationId; }
    /** @return tên participant. */
    public String participantService() { return participantService; }
    /** @return tên step. */
    public String stepName() { return stepName; }
    /** @return kết quả. */
    public Outcome outcome() { return outcome; }
    /** @return revision được ack. */
    public Long ackedRevision() { return ackedRevision; }
    /** @return epoch được ack. */
    public Long ackedEpoch() { return ackedEpoch; }
    /** @return version mong đợi. */
    public Long expectedVersion() { return expectedVersion; }
    /** @return mã lỗi. */
    public String errorCode() { return errorCode; }
    /** @return thông điệp lỗi. */
    public String errorMessage() { return errorMessage; }
    /** @return correlation id. */
    public String correlationId() { return correlationId; }
    /** @return causation id. */
    public String causationId() { return causationId; }
}