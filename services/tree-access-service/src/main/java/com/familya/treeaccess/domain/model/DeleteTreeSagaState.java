package com.familya.treeaccess.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Authoritative state of a delete-tree Saga. Owned by Tree Access per ADR-003.
 * {@link State} governs valid transitions; the irreversible boundary is the
 * {@link State#FINALIZING} -> {@link State#DELETION_FINALIZED} transition.
 */
public final class DeleteTreeSagaState {

    private final UUID operationId;
    private final UUID treeId;
    private final UUID initiatingUserId;
    private final UUID correlationId;
    private State state;
    private final long targetAggregateVersion;
    private final long targetEpoch;
    private final Instant deadlineAt;
    private final Instant startedAt;
    private Instant finalizedAt;
    private Instant lastUpdatedAt;
    private Instant irreversibleAt;
    private String failureCode;
    private String failureMessage;

    /**
     * @param operationId          mã thao tác Saga
     * @param treeId               mã cây
     * @param initiatingUserId     UUID người khởi tạo
     * @param correlationId        mã correlation
     * @param state                trạng thái khởi tạo
     * @param targetAggregateVersion barrier version
     * @param targetEpoch          barrier epoch
     * @param deadlineAt           deadline của Saga
     * @param startedAt            thời điểm bắt đầu
     * @param finalizedAt          thời điểm kết thúc hoặc {@code null}
     * @param lastUpdatedAt        thời điểm cập nhật lần cuối
     * @param irreversibleAt       thời điểm qua rào chắn hoặc {@code null}
     * @param failureCode          mã lỗi hoặc {@code null}
     * @param failureMessage       thông điệp lỗi hoặc {@code null}
     */
    public DeleteTreeSagaState(UUID operationId, UUID treeId, UUID initiatingUserId,
                               UUID correlationId, State state,
                               long targetAggregateVersion, long targetEpoch,
                               Instant deadlineAt, Instant startedAt, Instant finalizedAt,
                               Instant lastUpdatedAt, Instant irreversibleAt,
                               String failureCode, String failureMessage) {
        this.operationId = Objects.requireNonNull(operationId);
        this.treeId = Objects.requireNonNull(treeId);
        this.initiatingUserId = Objects.requireNonNull(initiatingUserId);
        this.correlationId = Objects.requireNonNull(correlationId);
        this.state = Objects.requireNonNull(state);
        this.targetAggregateVersion = targetAggregateVersion;
        this.targetEpoch = targetEpoch;
        this.deadlineAt = Objects.requireNonNull(deadlineAt);
        this.startedAt = Objects.requireNonNull(startedAt);
        this.finalizedAt = finalizedAt;
        this.lastUpdatedAt = Objects.requireNonNull(lastUpdatedAt);
        this.irreversibleAt = irreversibleAt;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
    }

    /**
     * @return mã thao tác Saga
     */
    public UUID operationId() { return operationId; }
    /**
     * @return mã cây đang xử lý
     */
    public UUID treeId() { return treeId; }
    /**
     * @return UUID người khởi tạo Saga
     */
    public UUID initiatingUserId() { return initiatingUserId; }
    /**
     * @return mã correlation dùng để truy vết
     */
    public UUID correlationId() { return correlationId; }
    /**
     * @return trạng thái hiện tại
     */
    public State state() { return state; }
    /**
     * @return version tổng hợp mục tiêu (barrier)
     */
    public long targetAggregateVersion() { return targetAggregateVersion; }
    /**
     * @return epoch mục tiêu (barrier)
     */
    public long targetEpoch() { return targetEpoch; }
    /**
     * @return deadline của Saga
     */
    public Instant deadlineAt() { return deadlineAt; }
    /**
     * @return thời điểm bắt đầu
     */
    public Instant startedAt() { return startedAt; }
    /**
     * @return thời điểm kết thúc (chỉ có khi Saga ở trạng thái terminal) hoặc {@code null}
     */
    public Instant finalizedAt() { return finalizedAt; }
    /**
     * @return thời điểm cập nhật lần cuối
     */
    public Instant lastUpdatedAt() { return lastUpdatedAt; }
    /**
     * @return thời điểm qua rào chắn không thể đảo ngược, hoặc {@code null}
     */
    public Instant irreversibleAt() { return irreversibleAt; }
    /**
     * @return mã lỗi đã ghi nhận, có thể {@code null}
     */
    public String failureCode() { return failureCode; }
    /**
     * @return thông điệp lỗi, có thể {@code null}
     */
    public String failureMessage() { return failureMessage; }

    /**
     * Chuyển trạng thái Saga theo bảng chuyển tiếp hợp lệ.
     *
     * @param next trạng thái tiếp theo
     * @param now  thời điểm chuyển
     * @throws IllegalStateException nếu chuyển tiếp không hợp lệ
     */
    public void transitionTo(State next, Instant now) {
        if (this.state.allowedNext(next) == null) {
            throw new IllegalStateException("Illegal delete-tree Saga transition " + this.state + " -> " + next);
        }
        this.state = next;
        this.lastUpdatedAt = now;
        if (next.isTerminal()) this.finalizedAt = now;
        if (next == State.FINALIZING && irreversibleAt == null) {
            // Đánh dấu đã qua rào chắn không thể đảo ngược.
            this.irreversibleAt = now;
        }
    }

    /**
     * Ghi nhận lỗi mà không thay đổi trạng thái.
     *
     * @param code    mã lỗi
     * @param message thông điệp
     * @param now     thời điểm ghi nhận
     */
    public void recordFailure(String code, String message, Instant now) {
        if (this.state.isTerminal()) {
            return;
        }
        this.failureCode = code;
        this.failureMessage = message;
        this.lastUpdatedAt = now;
    }

    /**
     * Tập trạng thái của Saga delete-tree.
     */
    public enum State {
        /** Saga mới khởi tạo, chưa xử lý bước nào. */
        PENDING,
        /** Đang thực hiện đóng băng cây. */
        FREEZING,
        /** Đang thực hiện tombstone. */
        TOMBSTONING,
        /** Đang fan-out tới các tham gia viên để xoá dữ liệu bounded-context. */
        PURGING,
        /** Đang chạy bước finalize (commit cuối cùng, không thể đảo ngược). */
        FINALIZING,
        /** Đang chạy compensation để bù lại các bước đã ACK. */
        COMPENSATING,
        /** Saga kết thúc thành công. */
        SUCCEEDED,
        /** Saga kết thúc thất bại không thể bù. */
        FAILED,
        /** Saga cần được con người xử lý thủ công. */
        MANUAL_REVIEW,
        /** Saga bị huỷ theo yêu cầu. */
        CANCELLED;

        /**
         * @return {@code true} nếu trạng thái hiện tại là terminal (kết thúc Saga)
         */
        public boolean isTerminal() {
            return this == SUCCEEDED || this == FAILED || this == MANUAL_REVIEW || this == CANCELLED;
        }

        /**
         * Trạng thái tiếp theo được phép, hoặc {@code null} nếu chuyển tiếp không hợp lệ.
         *
         * @param next trạng thái muốn chuyển tới
         * @return {@code next} nếu hợp lệ, {@code null} nếu không
         */
        public State allowedNext(State next) {
            return switch (this) {
                case PENDING       -> next == FREEZING || next == COMPENSATING || next == CANCELLED || next == MANUAL_REVIEW || next == FAILED ? next : null;
                case FREEZING      -> next == TOMBSTONING || next == COMPENSATING || next == MANUAL_REVIEW || next == FAILED ? next : null;
                case TOMBSTONING   -> next == PURGING || next == COMPENSATING || next == MANUAL_REVIEW || next == FAILED ? next : null;
                case PURGING       -> next == FINALIZING || next == COMPENSATING || next == MANUAL_REVIEW || next == FAILED ? next : null;
                case FINALIZING    -> next == SUCCEEDED || next == FAILED || next == MANUAL_REVIEW ? next : null;
                case COMPENSATING  -> next == SUCCEEDED || next == FAILED || next == MANUAL_REVIEW || next == CANCELLED ? next : null;
                case SUCCEEDED, FAILED, MANUAL_REVIEW, CANCELLED -> null;
            };
        }
    }
}