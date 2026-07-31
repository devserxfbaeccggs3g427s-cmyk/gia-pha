package com.familya.member.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class DeleteMemberSagaStep {

    private final UUID operationId;
    private final int sequenceNo;
    private final String stepCode;
    private final String participantService;
    private final boolean required;
    private final boolean compensatable;
    private State state;
    private int attemptCount;
    private final int maxAttempts;
    private Instant nextAttemptAt;
    private Instant lastDispatchedAt;
    private Instant stepDeadlineAt;
    private UUID dispatchToken;
    private Instant lastFailureAt;
    private Instant lastReplyAt;
    private Long appliedAggregateVersion;
    private Long appliedEpoch;
    private String failureCode;
    private String failureMessage;

    public DeleteMemberSagaStep(UUID operationId, int sequenceNo, String stepCode,
                                String participantService, boolean required, boolean compensatable,
                                State state, int attemptCount, int maxAttempts,
                                Instant nextAttemptAt,
                                Instant lastDispatchedAt, Instant stepDeadlineAt,
                                UUID dispatchToken, Instant lastFailureAt,
                                Instant lastReplyAt,
                                Long appliedAggregateVersion, Long appliedEpoch,
                                String failureCode, String failureMessage) {
        this.operationId = Objects.requireNonNull(operationId);
        this.sequenceNo = sequenceNo;
        this.stepCode = Objects.requireNonNull(stepCode);
        this.participantService = Objects.requireNonNull(participantService);
        this.required = required;
        this.compensatable = compensatable;
        this.state = Objects.requireNonNull(state);
        this.attemptCount = attemptCount;
        this.maxAttempts = maxAttempts;
        this.nextAttemptAt = nextAttemptAt;
        this.lastDispatchedAt = lastDispatchedAt;
        this.stepDeadlineAt = stepDeadlineAt;
        this.dispatchToken = dispatchToken;
        this.lastFailureAt = lastFailureAt;
        this.lastReplyAt = lastReplyAt;
        this.appliedAggregateVersion = appliedAggregateVersion;
        this.appliedEpoch = appliedEpoch;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
    }

    /** Mã operationId của Saga. */
    public UUID operationId() { return operationId; }
    /** Số thứ tự bước trong Saga (1..N). */
    public int sequenceNo() { return sequenceNo; }
    /** Mã bước Saga (ví dụ: TOMBSTONE_MEMBER, DISABLE_RELATIONSHIPS). */
    public String stepCode() { return stepCode; }
    /** Tên dịch vụ tham gia thực hiện bước này. */
    public String participantService() { return participantService; }
    /** Cờ bắt buộc: bước phải thành công để Saga hoàn tất. */
    public boolean required() { return required; }
    /** Cờ có thể bù trừ: cho phép chạy compensation khi Saga rollback. */
    public boolean compensatable() { return compensatable; }
    /** Trạng thái hiện tại của bước. */
    public State state() { return state; }
    /** Số lần đã thử. */
    public int attemptCount() { return attemptCount; }
    /** Số lần thử tối đa. */
    public int maxAttempts() { return maxAttempts; }
    /** Thời điểm dự kiến thử lại tiếp theo. */
    public Instant nextAttemptAt() { return nextAttemptAt; }
    /** Thời điểm dispatch gần nhất. */
    public Instant lastDispatchedAt() { return lastDispatchedAt; }
    /** Deadline của lần dispatch hiện tại. */
    public Instant stepDeadlineAt() { return stepDeadlineAt; }
    /** Token định danh lần dispatch hiện tại (dùng cho idempotency). */
    public UUID dispatchToken() { return dispatchToken; }
    /** Thời điểm thất bại gần nhất. */
    public Instant lastFailureAt() { return lastFailureAt; }
    /** Thời điểm reply gần nhất. */
    public Instant lastReplyAt() { return lastReplyAt; }
    /** Phiên bản aggregate đã áp dụng (có thể null). */
    public Long appliedAggregateVersion() { return appliedAggregateVersion; }
    /** Epoch đã áp dụng (có thể null). */
    public Long appliedEpoch() { return appliedEpoch; }
    /** Mã lỗi của lần thất bại gần nhất. */
    public String failureCode() { return failureCode; }
    /** Mô tả lỗi của lần thất bại gần nhất. */
    public String failureMessage() { return failureMessage; }

    /**
     * Giành quyền dispatch cho bước Saga. Trả về {@code true} nếu giành được.
     * Không làm gì nếu bước đã ở trạng thái cuối hoặc token đã khớp.
     *
     * @param token      token định danh lần dispatch
     * @param now        thời điểm dispatch
     * @param deadlineAt deadline của bước
     * @return {@code true} nếu giành được
     */
    public boolean claimDispatch(UUID token, Instant now, Instant deadlineAt) {
        Objects.requireNonNull(token, "dispatchToken");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(deadlineAt, "stepDeadlineAt");
        if (isTerminal()) {
            return false;
        }
        if (state == State.DISPATCHED) {
            return token.equals(this.dispatchToken);
        }
        this.attemptCount = this.attemptCount + 1;
        this.state = State.DISPATCHED;
        this.dispatchToken = token;
        this.lastDispatchedAt = now;
        this.stepDeadlineAt = deadlineAt;
        this.nextAttemptAt = null;
        return true;
    }

    /**
     * Đánh dấu bước Saga là ACK với version/epoch đã áp dụng.
     *
     * @param now                   thời điểm ACK
     * @param appliedAggregateVersion phiên bản aggregate
     * @param appliedEpoch          epoch
     */
    public void acknowledge(Instant now, long appliedAggregateVersion, long appliedEpoch) {
        Objects.requireNonNull(now, "now");
        if (state == State.ACK) {
            this.lastReplyAt = now;
            this.appliedAggregateVersion = appliedAggregateVersion;
            this.appliedEpoch = appliedEpoch;
            return;
        }
        this.state = State.ACK;
        this.lastReplyAt = now;
        this.appliedAggregateVersion = appliedAggregateVersion;
        this.appliedEpoch = appliedEpoch;
        this.nextAttemptAt = null;
        this.stepDeadlineAt = null;
    }

    /**
     * Lên lịch retry cho bước: chuyển sang FAILED với thời điểm retry kế tiếp.
     *
     * @param now           thời điểm lên lịch
     * @param nextAttemptAt thời điểm retry
     * @param code          mã lỗi
     * @param message       mô tả lỗi
     */
    public void scheduleRetry(Instant now, Instant nextAttemptAt, String code, String message) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
        if (isTerminal()) {
            return;
        }
        this.state = State.FAILED;
        this.lastReplyAt = now;
        this.lastFailureAt = now;
        this.failureCode = code;
        this.failureMessage = message;
        this.nextAttemptAt = nextAttemptAt;
        this.stepDeadlineAt = null;
        this.dispatchToken = null;
    }

    /**
     * Đánh dấu bước Saga là COMPENSATED (đã bù trừ thành công).
     *
     * @param now thời điểm compensate
     */
    public void markCompensated(Instant now) {
        Objects.requireNonNull(now, "now");
        this.state = State.COMPENSATED;
        this.lastReplyAt = now;
        this.nextAttemptAt = null;
        this.stepDeadlineAt = null;
    }

    /**
     * Đánh dấu bước Saga là DEAD_LETTERED (không thể xử lý tiếp).
     */
    public void markFailed(String code, String message, Instant now) {
        Objects.requireNonNull(now, "now");
        this.state = State.DEAD_LETTERED;
        this.lastReplyAt = now;
        this.lastFailureAt = now;
        this.failureCode = code;
        this.failureMessage = message;
        this.nextAttemptAt = null;
        this.stepDeadlineAt = null;
    }

    /** Trả về {@code true} nếu đã hết lượt thử. */
    public boolean exhausted() { return attemptCount >= maxAttempts; }

    /**
     * Kiểm tra bước đã đến thời điểm retry chưa (FAILED + còn lượt + đã quá nextAttemptAt).
     */
    public boolean retryDue(Instant now) {
        Objects.requireNonNull(now, "now");
        if (state != State.FAILED) return false;
        if (exhausted()) return false;
        if (nextAttemptAt == null) return false;
        return !now.isBefore(nextAttemptAt);
    }

    /**
     * Kiểm tra bước đang DISPATCHED đã quá deadline riêng hay chưa.
     */
    public boolean timedOut(Instant now) {
        Objects.requireNonNull(now, "now");
        if (state != State.DISPATCHED) return false;
        if (stepDeadlineAt == null) return false;
        return !now.isBefore(stepDeadlineAt);
    }

    /** Trả về {@code true} nếu bước đã ở trạng thái cuối (ACK/COMPENSATED/DEAD_LETTERED). */
    public boolean isTerminal() {
        return state == State.ACK || state == State.COMPENSATED || state == State.DEAD_LETTERED;
    }

    /**
     * Đánh dấu compensation đã được dispatch cho bước. Trả về {@code true} nếu thành công.
     * Chỉ áp dụng khi bước đang ở trạng thái ACK.
     */
    public boolean markCompensationDispatched(Instant now, UUID token, Instant stepDeadlineAt) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(stepDeadlineAt, "stepDeadlineAt");
        if (isTerminal()) {
            return false;
        }
        if (state == State.ACK) {
            this.state = State.DISPATCHED;
            this.lastDispatchedAt = now;
            this.dispatchToken = token;
            this.stepDeadlineAt = stepDeadlineAt;
            this.nextAttemptAt = null;
            return true;
        }
        return false;
    }

    /**
     * Đánh dấu bước Saga đã được dispatch (chuyển sang DISPATCHED và tăng attempt).
     */
    public void dispatch(Instant now) {
        Objects.requireNonNull(now, "now");
        if (isTerminal()) return;
        if (state == State.DISPATCHED) {
            this.lastDispatchedAt = now;
            return;
        }
        this.attemptCount = this.attemptCount + 1;
        this.state = State.DISPATCHED;
        this.lastDispatchedAt = now;
        this.nextAttemptAt = null;
    }

    /** Bí danh ngắn cho {@link #acknowledge}. */
    public void ack(Instant now, long appliedAggregateVersion, long appliedEpoch) {
        acknowledge(now, appliedAggregateVersion, appliedEpoch);
    }

    /**
     * Đánh dấu bước Saga là FAILED (thất bại nghiệp vụ, có thể retry hoặc compensate).
     */
    public void fail(String code, String message, Instant now) {
        Objects.requireNonNull(now, "now");
        if (isTerminal()) return;
        this.state = State.FAILED;
        this.lastReplyAt = now;
        this.lastFailureAt = now;
        this.failureCode = code;
        this.failureMessage = message;
    }

    /** Trả về {@code true} nếu bước đang ACK và sẵn sàng cho bước tiếp theo. */
    public boolean isReadyForNext() { return state == State.ACK; }

    /** Đánh dấu bước Saga đã được compensate thành công (bí danh). */
    public void compensate(Instant now) {
        markCompensated(now);
    }

    /** Đánh dấu bước Saga dead-lettered (bí danh). */
    public void markDeadLettered(String code, String message, Instant now) {
        markFailed(code, message, now);
    }

    /**
     * Tập trạng thái của một bước Saga.
     */
    public enum State {
        /** Bước đã được tạo nhưng chưa dispatch. */
        PENDING,
        /** Bước đã dispatch nhưng chưa nhận reply. */
        DISPATCHED,
        /** Bước đã được xác nhận thành công. */
        ACK,
        /** Bước thất bại nhưng có thể retry. */
        FAILED,
        /** Bước đã được bù trừ thành công. */
        COMPENSATED,
        /** Bước đã hết lượt thử và không thể xử lý tiếp. */
        DEAD_LETTERED
    }
}
