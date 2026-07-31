package com.familya.member.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Authoritative state of a delete-member Saga. Owned by the Member service per
 * ADR-003. {@code state} transitions are governed by
 * {@link com.familya.member.application.usecase.DeleteMemberSagaService}.
 */
public final class DeleteMemberSagaState {

    private final UUID operationId;
    private final UUID treeId;
    private final UUID memberId;
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

    public DeleteMemberSagaState(UUID operationId, UUID treeId, UUID memberId,
                                 UUID initiatingUserId, UUID correlationId,
                                 State state, long targetAggregateVersion, long targetEpoch,
                                 Instant deadlineAt, Instant startedAt, Instant finalizedAt,
                                 Instant lastUpdatedAt, Instant irreversibleAt,
                                 String failureCode, String failureMessage) {
        this.operationId = Objects.requireNonNull(operationId);
        this.treeId = Objects.requireNonNull(treeId);
        this.memberId = Objects.requireNonNull(memberId);
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

    /** Mã operationId của Saga. */
    public UUID operationId() { return operationId; }
    /** Mã cây. */
    public UUID treeId() { return treeId; }
    /** Mã thành viên. */
    public UUID memberId() { return memberId; }
    /** Người dùng khởi tạo Saga. */
    public UUID initiatingUserId() { return initiatingUserId; }
    /** Mã tương quan (correlation). */
    public UUID correlationId() { return correlationId; }
    /** Trạng thái hiện tại. */
    public State state() { return state; }
    /** Phiên bản aggregate mục tiêu (cho barrier). */
    public long targetAggregateVersion() { return targetAggregateVersion; }
    /** Epoch mục tiêu (cho barrier). */
    public long targetEpoch() { return targetEpoch; }
    /** Deadline tuyệt đối của Saga. */
    public Instant deadlineAt() { return deadlineAt; }
    /** Thời điểm bắt đầu. */
    public Instant startedAt() { return startedAt; }
    /** Thời điểm cuối cùng (khi vào trạng thái cuối). */
    public Instant finalizedAt() { return finalizedAt; }
    /** Thời điểm cập nhật gần nhất. */
    public Instant lastUpdatedAt() { return lastUpdatedAt; }
    /** Thời điểm đã qua irreversible boundary (có thể null). */
    public Instant irreversibleAt() { return irreversibleAt; }
    /** Mã lỗi nếu có. */
    public String failureCode() { return failureCode; }
    /** Mô tả lỗi nếu có. */
    public String failureMessage() { return failureMessage; }

    /**
     * Chuyển trạng thái Saga theo biểu đồ chuyển trạng thái hợp lệ.
     *
     * @param next trạng thái tiếp theo
     * @param now  thời điểm chuyển
     * @throws IllegalStateException nếu chuyển trạng thái không hợp lệ
     */
    public void transitionTo(State next, Instant now) {
        State allowed = this.state.allowedNext(next);
        if (allowed == null) {
            throw new IllegalStateException(
                    "Illegal delete-member Saga transition " + this.state + " -> " + next);
        }
        this.state = next;
        this.lastUpdatedAt = now;
        if (next.isTerminal()) {
            this.finalizedAt = now;
        }
    }

    /**
     * Đánh dấu Saga đã qua irreversible boundary (không thể rollback).
     * Idempotent: nếu đã đánh dấu thì không thay đổi.
     *
     * @param now thời điểm đánh dấu
     */
    public void markIrreversible(Instant now) {
        if (this.irreversibleAt == null) {
            this.irreversibleAt = now;
            this.lastUpdatedAt = now;
        }
    }

    /**
     * Ghi nhận lỗi mà không chuyển trạng thái. Bỏ qua nếu Saga đã ở trạng thái cuối.
     *
     * @param code    mã lỗi
     * @param message mô tả lỗi
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
     * Tập trạng thái của Saga xóa thành viên. Bao gồm các trạng thái không cuối
     * (PENDING, DISPATCHED, COMPENSATING) và các trạng thái cuối (SUCCEEDED, FAILED,
     * MANUAL_REVIEW, CANCELLED).
     */
    public enum State {
        /** Saga vừa được khởi tạo, chưa dispatch bước nào. */
        PENDING,
        /** Đã dispatch ít nhất một bước forward. */
        DISPATCHED,
        /** Đang trong giai đoạn bù trừ. */
        COMPENSATING,
        /** Saga hoàn tất thành công. */
        SUCCEEDED,
        /** Saga thất bại (compensation thất bại hoặc không thể compensate). */
        FAILED,
        /** Saga cần con người can thiệp. */
        MANUAL_REVIEW,
        /** Saga bị hủy. */
        CANCELLED;

        /** Trả về {@code true} nếu trạng thái hiện tại là cuối (không thể chuyển tiếp). */
        public boolean isTerminal() {
            return this == SUCCEEDED || this == FAILED || this == MANUAL_REVIEW || this == CANCELLED;
        }

        /**
         * Kiểm tra trạng thái tiếp theo có hợp lệ hay không. Trả về {@code next} nếu hợp lệ, {@code null} nếu không.
         */
        public State allowedNext(State next) {
            return switch (this) {
                case PENDING       -> next == DISPATCHED || next == COMPENSATING || next == CANCELLED || next == MANUAL_REVIEW || next == FAILED ? next : null;
                case DISPATCHED    -> next == COMPENSATING || next == SUCCEEDED || next == FAILED || next == MANUAL_REVIEW || next == CANCELLED ? next : null;
                case COMPENSATING  -> next == SUCCEEDED || next == FAILED || next == MANUAL_REVIEW || next == CANCELLED ? next : null;
                case SUCCEEDED, FAILED, MANUAL_REVIEW, CANCELLED -> null;
            };
        }
    }
}