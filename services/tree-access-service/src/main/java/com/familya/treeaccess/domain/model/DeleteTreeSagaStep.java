package com.familya.treeaccess.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class DeleteTreeSagaStep {

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

    /**
     * @param operationId           mã thao tác Saga
     * @param sequenceNo            số thứ tự bước
     * @param stepCode              mã bước
     * @param participantService    tên service tham gia
     * @param required              bước có bắt buộc không
     * @param compensatable         bước có bù được không
     * @param state                 trạng thái khởi tạo
     * @param attemptCount          số lần đã thử
     * @param maxAttempts           số lần thử tối đa
     * @param nextAttemptAt         thời điểm retry kế tiếp hoặc {@code null}
     * @param lastDispatchedAt      thời điểm dispatch gần nhất hoặc {@code null}
     * @param stepDeadlineAt        deadline step hoặc {@code null}
     * @param dispatchToken         token dispatch hoặc {@code null}
     * @param lastFailureAt         thời điểm lỗi gần nhất hoặc {@code null}
     * @param lastReplyAt           thời điểm reply gần nhất hoặc {@code null}
     * @param appliedAggregateVersion version đã áp dụng hoặc {@code null}
     * @param appliedEpoch          epoch đã áp dụng hoặc {@code null}
     * @param failureCode           mã lỗi hoặc {@code null}
     * @param failureMessage        thông điệp lỗi hoặc {@code null}
     */
    public DeleteTreeSagaStep(UUID operationId, int sequenceNo, String stepCode,
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

    /**
     * @return mã thao tác Saga chứa bước này
     */
    public UUID operationId() { return operationId; }
    /**
     * @return số thứ tự bước trong Saga
     */
    public int sequenceNo() { return sequenceNo; }
    /**
     * @return mã bước (vd {@code PURGE_MEMBER_TREE})
     */
    public String stepCode() { return stepCode; }
    /**
     * @return tên service tham gia xử lý bước
     */
    public String participantService() { return participantService; }
    /**
     * @return {@code true} nếu bước bắt buộc (Saga thất bại nếu bước này lỗi)
     */
    public boolean required() { return required; }
    /**
     * @return {@code true} nếu bước có thể bù (compensate)
     */
    public boolean compensatable() { return compensatable; }
    /**
     * @return trạng thái hiện tại của bước
     */
    public State state() { return state; }
    /**
     * @return số lần đã thử
     */
    public int attemptCount() { return attemptCount; }
    /**
     * @return số lần thử tối đa
     */
    public int maxAttempts() { return maxAttempts; }
    /**
     * @return thời điểm retry kế tiếp hoặc {@code null}
     */
    public Instant nextAttemptAt() { return nextAttemptAt; }
    /**
     * @return thời điểm dispatch gần nhất
     */
    public Instant lastDispatchedAt() { return lastDispatchedAt; }
    /**
     * @return deadline của step
     */
    public Instant stepDeadlineAt() { return stepDeadlineAt; }
    /**
     * @return token dùng để kiểm tra quyền dispatch
     */
    public UUID dispatchToken() { return dispatchToken; }
    /**
     * @return thời điểm thất bại gần nhất hoặc {@code null}
     */
    public Instant lastFailureAt() { return lastFailureAt; }
    /**
     * @return thời điểm reply gần nhất hoặc {@code null}
     */
    public Instant lastReplyAt() { return lastReplyAt; }
    /**
     * @return phiên bản tổng hợp đã áp dụng hoặc {@code null}
     */
    public Long appliedAggregateVersion() { return appliedAggregateVersion; }
    /**
     * @return epoch đã áp dụng hoặc {@code null}
     */
    public Long appliedEpoch() { return appliedEpoch; }
    /**
     * @return mã lỗi hoặc {@code null}
     */
    public String failureCode() { return failureCode; }
    /**
     * @return thông điệp lỗi hoặc {@code null}
     */
    public String failureMessage() { return failureMessage; }

    /**
     * Chiếm quyền dispatch cho bước này. Nếu đã ở DISPATCHED với cùng token thì
     * idempotent; ngược lại chuyển sang DISPATCHED, tăng attemptCount.
     *
     * @param token      token do worker phát ra
     * @param now        thời điểm chiếm
     * @param deadlineAt deadline mà worker kỳ vọng nhận ACK
     * @return {@code true} nếu claim thành công hoặc đã có sẵn token trùng
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
     * Đánh dấu bước đã được ACK thành công.
     *
     * @param now                    thời điểm ACK
     * @param appliedAggregateVersion phiên bản tổng hợp đã áp dụng
     * @param appliedEpoch            epoch đã áp dụng
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
     * Lên lịch retry cho một bước thất bại.
     *
     * @param now           thời điểm lên lịch
     * @param nextAttemptAt thời điểm retry kế tiếp
     * @param code          mã lỗi
     * @param message       thông điệp lỗi
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
     * Đánh dấu bước đã được bù thành công (compensation hoàn tất).
     *
     * @param now thời điểm bù xong
     */
    public void markCompensated(Instant now) {
        Objects.requireNonNull(now, "now");
        this.state = State.COMPENSATED;
        this.lastReplyAt = now;
        this.nextAttemptAt = null;
        this.stepDeadlineAt = null;
    }

    /**
     * Đánh dấu bước thất bại (chuyển sang DEAD_LETTERED).
     *
     * @param code    mã lỗi
     * @param message thông điệp
     * @param now     thời điểm lỗi
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

    /**
     * @return {@code true} nếu bước đã hết số lần thử tối đa
     */
    public boolean exhausted() { return attemptCount >= maxAttempts; }

    /**
     * @param now thời điểm hiện tại
     * @return {@code true} nếu bước đang FAILED, đã tới {@code nextAttemptAt} và chưa hết lượt
     */
    public boolean retryDue(Instant now) {
        Objects.requireNonNull(now, "now");
        if (state != State.FAILED) return false;
        if (exhausted()) return false;
        if (nextAttemptAt == null) return false;
        return !now.isBefore(nextAttemptAt);
    }

    /**
     * @param now thời điểm hiện tại
     * @return {@code true} nếu bước đang DISPATCHED mà đã vượt {@code stepDeadlineAt}
     */
    public boolean timedOut(Instant now) {
        Objects.requireNonNull(now, "now");
        if (state != State.DISPATCHED) return false;
        if (stepDeadlineAt == null) return false;
        return !now.isBefore(stepDeadlineAt);
    }

    /**
     * @return {@code true} nếu bước đã ở một trạng thái kết thúc (ACK/COMPENSATED/DEAD_LETTERED)
     */
    public boolean isTerminal() {
        return state == State.ACK || state == State.COMPENSATED || state == State.DEAD_LETTERED;
    }

    /**
     * Đánh dấu một bước đã ACK được dispatch compensation.
     *
     * @param now           thời điểm dispatch compensation
     * @param token         token claim
     * @param stepDeadlineAt deadline compensation
     * @return {@code true} nếu việc chuyển trạng thái thành công
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
     * Đánh dấu bước ở DISPATCHED và tăng attemptCount nếu chưa ở DISPATCHED.
     *
     * @param now thời điểm dispatch
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

    /**
     * Đường tắt cho {@link #acknowledge(Instant, long, long)}.
     *
     * @param now                    thời điểm ACK
     * @param appliedAggregateVersion phiên bản tổng hợp đã áp dụng
     * @param appliedEpoch            epoch đã áp dụng
     */
    public void ack(Instant now, long appliedAggregateVersion, long appliedEpoch) {
        acknowledge(now, appliedAggregateVersion, appliedEpoch);
    }

    /**
     * Đánh dấu bước thất bại (chuyển sang FAILED).
     *
     * @param code    mã lỗi
     * @param message thông điệp
     * @param now     thời điểm thất bại
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

    /**
     * Đường tắt cho {@link #markCompensated(Instant)}.
     *
     * @param now thời điểm bù xong
     */
    public void compensate(Instant now) {
        markCompensated(now);
    }

    /**
     * Đường tắt cho {@link #markFailed(String, String, Instant)}.
     *
     * @param code    mã lỗi
     * @param message thông điệp
     * @param now     thời điểm lỗi
     */
    public void markDeadLettered(String code, String message, Instant now) {
        markFailed(code, message, now);
    }

    /** Tập trạng thái của một bước Saga delete-tree. */
    public enum State { PENDING, DISPATCHED, ACK, FAILED, COMPENSATED, DEAD_LETTERED }
}
