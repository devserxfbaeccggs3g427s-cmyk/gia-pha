package com.familya.member.application.usecase;

import com.familya.member.adapter.in.kafka.DeleteMemberSagaDeadLetterStore;
import com.familya.member.application.port.in.DeleteMemberSagaReplyCommand;
import com.familya.member.application.port.out.DeleteMemberSagaGateway;
import com.familya.member.application.port.out.DeleteMemberSagaRepository;
import com.familya.member.domain.model.DeleteMemberSagaState;
import com.familya.member.domain.model.DeleteMemberSagaStep;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Bộ xử lý reply Saga cho Saga xóa thành viên. Cập nhật trạng thái Saga dựa trên các
 * reply đến từ participant (ACK thành công, ACK thất bại, compensation reply).
 *
 * <p>Đây là bean {@code @Service} thuộc tầng application/usecase trong kiến trúc Hexagonal.
 */
@Service
public class DeleteMemberSagaReplyProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteMemberSagaReplyProcessor.class);

    private final DeleteMemberSagaRepository sagaRepo;
    private final DeleteMemberSagaGateway gateway;
    private final DeleteMemberSagaDeadLetterStore deadLetterStore;
    private final PlatformMetrics metrics;
    private final Clock clock;

    /**
     * Khởi tạo bộ xử lý reply.
     *
     * @param sagaRepo        kho Saga
     * @param gateway         gateway để stage các sự kiện
     * @param deadLetterStore kho dead-letter
     * @param metrics         bộ metric
     * @param clock           đồng hồ tiêm được
     */
    public DeleteMemberSagaReplyProcessor(DeleteMemberSagaRepository sagaRepo,
                                          DeleteMemberSagaGateway gateway,
                                          DeleteMemberSagaDeadLetterStore deadLetterStore,
                                          PlatformMetrics metrics,
                                          Clock clock) {
        this.sagaRepo = sagaRepo;
        this.gateway = gateway;
        this.deadLetterStore = deadLetterStore;
        this.metrics = metrics;
        this.clock = clock;
    }

    /**
     * Xử lý một reply Saga. Phương thức điều phối theo nhiều nhánh:
     * <ul>
     *   <li>Compensation reply: xử lý riêng (xem {@link #handleCompensationReply}).</li>
     *   <li>Forward reply khi đang compensating: bỏ qua.</li>
     *   <li>Reply trùng/stale: bỏ qua.</li>
     *   <li>Reply thất bại: gọi {@link #handleParticipantFailure}.</li>
     *   <li>Reply thành công không thỏa barrier: gọi {@link #handleBarrierFailure}.</li>
     *   <li>Reply thành công: ACK bước, đánh dấu irreversible nếu &ge; sequenceNo 5, và
     *       dispatch bước tiếp theo hoặc chuyển Saga sang SUCCEEDED.</li>
     * </ul>
     */
    @Transactional
    public void process(DeleteMemberSagaReplyCommand cmd) {
        // Tra cứu Saga theo operationId; bỏ qua nếu không tồn tại
        Optional<DeleteMemberSagaState> opt = sagaRepo.findState(cmd.operationId());
        if (opt.isEmpty()) {
            LOG.warn("Ignoring reply for unknown operationId={}", cmd.operationId());
            return;
        }
        DeleteMemberSagaState state = opt.get();
        // Bỏ qua nếu Saga đã ở trạng thái cuối (SUCCEEDED, FAILED, MANUAL_REVIEW, CANCELLED)
        if (state.state().isTerminal()) {
            LOG.info("Ignoring reply for terminal Saga operationId={} state={}", state.operationId(), state.state());
            return;
        }

        List<DeleteMemberSagaStep> steps = sagaRepo.listSteps(cmd.operationId());
        // Tìm bước Saga khớp với reply dựa trên stepCode và participantService
        DeleteMemberSagaStep current = steps.stream()
                .filter(s -> s.stepCode().equals(cmd.stepCode()))
                .filter(s -> s.participantService().equals(cmd.participantService()))
                .findFirst()
                .orElse(null);
        if (current == null) {
            LOG.warn("Ignoring reply op={} step={} participant={} — no matching step",
                    cmd.operationId(), cmd.stepCode(), cmd.participantService());
            return;
        }

        Instant now = clock.now();
        metrics.mutationAccepted("member-service", "deleteMember.reply");

        // Phân loại reply: compensation, forward, v.v.
        if (cmd.compensationApplied()) {
            if (cmd.failed()) {
                handleCompensationFailure(state, current, cmd.failureCode(), cmd.failureMessage(), now);
            } else {
                handleCompensationReply(state, steps, current, now);
            }
            return;
        }
        // Reply forward nhưng Saga đang trong giai đoạn bù trừ: bỏ qua
        if (state.state() == DeleteMemberSagaState.State.COMPENSATING) {
            LOG.info("Ignoring forward reply while compensating operationId={} step={}",
                    state.operationId(), current.stepCode());
            return;
        }
        // Reply cho bước không ở trạng thái DISPATCHED: bỏ qua (stale)
        if (current.state() != DeleteMemberSagaStep.State.DISPATCHED) {
            LOG.info("Ignoring stale reply operationId={} step={} state={}",
                    state.operationId(), current.stepCode(), current.state());
            return;
        }

        if (cmd.failed()) {
            handleParticipantFailure(state, steps, current, cmd.failureCode(), cmd.failureMessage(), now);
            return;
        }

        // Kiểm tra barrier: phiên bản aggregate và epoch phải đạt mục tiêu
        if (!satisfiesBarrier(state, cmd)) {
            handleBarrierFailure(state, current, now);
            return;
        }

        // ACK bước hiện tại
        current.ack(now, cmd.appliedAggregateVersion(), cmd.appliedEpoch());
        sagaRepo.updateStep(current);
        // Sau sequenceNo 5, Saga đã qua irreversible boundary
        if (current.sequenceNo() >= 5) {
            state.markIrreversible(now);
        }

        // Tìm bước tiếp theo; nếu không còn thì chuyển Saga sang SUCCEEDED
        DeleteMemberSagaStep next = nextPendingStep(steps, current.sequenceNo());
        if (next == null) {
            state.transitionTo(DeleteMemberSagaState.State.SUCCEEDED, now);
            sagaRepo.saveState(state);
            gateway.stageOperationStateChanged(state);
            return;
        }

        // Dispatch bước tiếp theo
        dispatchStep(state, next, now);
        sagaRepo.saveState(state);
        gateway.stageOperationStateChanged(state);
    }

    /**
     * Xử lý reply cho compensation (thành công). Nếu đây là compensation cuối cùng cần thiết,
     * chuyển Saga sang FAILED.
     */
    private void handleCompensationReply(DeleteMemberSagaState state,
                                         List<DeleteMemberSagaStep> steps,
                                         DeleteMemberSagaStep current,
                                         Instant now) {
        if (state.state() != DeleteMemberSagaState.State.COMPENSATING
                || current.state() != DeleteMemberSagaStep.State.DISPATCHED) {
            return;
        }
        current.compensate(now);
        sagaRepo.updateStep(current);
        boolean complete = sagaRepo.listSteps(state.operationId()).stream()
                .filter(DeleteMemberSagaStep::compensatable)
                .noneMatch(s -> s.state() == DeleteMemberSagaStep.State.ACK
                        || s.state() == DeleteMemberSagaStep.State.DISPATCHED);
        if (complete) {
            state.transitionTo(DeleteMemberSagaState.State.FAILED, now);
            sagaRepo.saveState(state);
            gateway.stageOperationStateChanged(state);
        }
    }

    /**
     * Xử lý reply cho compensation (thất bại). Có thể thử lại hoặc leo thang sang MANUAL_REVIEW.
     */
    private void handleCompensationFailure(DeleteMemberSagaState state,
                                           DeleteMemberSagaStep current,
                                           String code, String message,
                                           Instant now) {
        if (state.state() != DeleteMemberSagaState.State.COMPENSATING
                || current.state() != DeleteMemberSagaStep.State.DISPATCHED) return;
        if (current.attemptCount() < current.maxAttempts()) {
            UUID token = UUID.randomUUID();
            Instant stepDeadline = now.plusSeconds(30);
            if (sagaRepo.tryClaimCompensation(current.operationId(), current.sequenceNo(),
                    token, now, stepDeadline)) {
                current.markCompensationDispatched(now, token, stepDeadline);
                sagaRepo.updateStep(current);
                gateway.stageCompensation(state, current);
            }
            return;
        }
        current.markDeadLettered(code, message, now);
        sagaRepo.updateStep(current);
        state.recordFailure(code, message, now);
        state.transitionTo(DeleteMemberSagaState.State.MANUAL_REVIEW, now);
        sagaRepo.saveState(state);
        deadLetterStore.saveRetryExhausted(
                state.operationId(), current.participantService(), current.stepCode(),
                current.attemptCount(), new IllegalStateException("Compensation failed: " + code + " " + message));
        gateway.stageOperationStateChanged(state);
    }

    /**
     * Xử lý reply thất bại từ participant (lỗi nghiệp vụ). Tùy số lần thử còn lại và vị trí
     * trong Saga mà retry, compensate hoặc leo thang MANUAL_REVIEW.
     */
    private void handleParticipantFailure(DeleteMemberSagaState state,
                                          List<DeleteMemberSagaStep> steps,
                                          DeleteMemberSagaStep current,
                                          String code, String message, Instant now) {
        current.fail(code, message, now);
        sagaRepo.updateStep(current);
        state.recordFailure(code, message, now);

        if (current.attemptCount() < current.maxAttempts()) {
            dispatchStep(state, current, now);
            sagaRepo.saveState(state);
            gateway.stageOperationStateChanged(state);
            return;
        }

        if (!passedIrreversibleBoundary(state, current)) {
            deadLetterStore.saveRetryExhausted(
                    state.operationId(), current.participantService(), current.stepCode(),
                    current.attemptCount(),
                    new IllegalStateException("Participant retry exhausted: " + code + " " + message));
            boolean stateChanged = state.state() != DeleteMemberSagaState.State.COMPENSATING;
            if (stateChanged) {
                state.transitionTo(DeleteMemberSagaState.State.COMPENSATING, now);
            }
            sagaRepo.saveState(state);
            if (stateChanged) {
                gateway.stageOperationStateChanged(state);
                compensatePreviousSteps(state, steps, current);
            }
            return;
        }

        current.markDeadLettered(code, message, now);
        sagaRepo.updateStep(current);
        state.transitionTo(DeleteMemberSagaState.State.MANUAL_REVIEW, now);
        sagaRepo.saveState(state);
        deadLetterStore.saveRetryExhausted(
                state.operationId(), current.participantService(), current.stepCode(),
                current.attemptCount(),
                new IllegalStateException("Participant retry exhausted: " + code + " " + message));
        gateway.stageOperationStateChanged(state);
    }

    /**
     * Xử lý khi reply không đạt barrier (phiên bản aggregate hoặc epoch chưa đạt mục tiêu).
     * Leo thang sang MANUAL_REVIEW để con người can thiệp.
     */
    private void handleBarrierFailure(DeleteMemberSagaState state,
                                      DeleteMemberSagaStep current, Instant now) {
        String code = "BARRIER_NOT_MET";
        String message = "step " + current.stepCode() + " did not meet barrier";
        current.fail(code, message, now);
        sagaRepo.updateStep(current);
        state.recordFailure(code, message, now);
        state.transitionTo(DeleteMemberSagaState.State.MANUAL_REVIEW, now);
        sagaRepo.saveState(state);
        gateway.stageOperationStateChanged(state);
    }

    /**
     * Dispatch một bước Saga: giành quyền dispatch và stage lệnh forward lên outbox.
     * Tránh dispatch trùng bằng cách kiểm tra {@code dispatch_token}.
     */
    private void dispatchStep(DeleteMemberSagaState state, DeleteMemberSagaStep step, Instant now) {
        if (step.attemptCount() > 0 && step.dispatchToken() != null && step.state() == DeleteMemberSagaStep.State.DISPATCHED) {
            sagaRepo.updateStep(step);
            gateway.stageFirstStep(state, step);
            return;
        }
        UUID token = UUID.randomUUID();
        Instant stepDeadline = now.plusSeconds(30);
        boolean claimed = sagaRepo.tryClaimDispatch(step.operationId(), step.sequenceNo(), token, now, stepDeadline);
        if (!claimed) {
            LOG.warn("dispatchStep claim conflict op={} seq={} step.state={}; skipping publish to avoid double dispatch",
                    step.operationId(), step.sequenceNo(), step.state());
            return;
        }
        step.claimDispatch(token, now, stepDeadline);
        sagaRepo.updateStep(step);
        gateway.stageFirstStep(state, step);
    }

    /** Xác định Saga đã qua irreversible boundary (cấp operation hoặc bước &ge; 5). */
    private static boolean passedIrreversibleBoundary(DeleteMemberSagaState state, DeleteMemberSagaStep step) {
        return state.irreversibleAt() != null || step.sequenceNo() >= 5;
    }

    /**
     * Kiểm tra reply có đạt barrier mục tiêu không.
     *
     * @param state trạng thái Saga (chứa target version/epoch)
     * @param cmd   reply cần kiểm tra
     * @return {@code true} nếu phiên bản và epoch của reply &ge; mục tiêu
     */
    private boolean satisfiesBarrier(DeleteMemberSagaState state, DeleteMemberSagaReplyCommand cmd) {
        return cmd.appliedAggregateVersion() >= state.targetAggregateVersion()
                && cmd.appliedEpoch() >= state.targetEpoch();
    }

    /** Tìm bước tiếp theo còn ở trạng thái PENDING. */
    private static DeleteMemberSagaStep nextPendingStep(List<DeleteMemberSagaStep> steps, int currentSeq) {
        return steps.stream()
                .filter(s -> s.sequenceNo() > currentSeq)
                .filter(s -> s.state() == DeleteMemberSagaStep.State.PENDING)
                .findFirst()
                .orElse(null);
    }

    /**
     * Bù trừ các bước trước bước thất bại (theo thứ tự ngược LIFO). Chỉ các bước
     * compensatable, đang ACK và chưa qua irreversible boundary mới được bù trừ.
     */
    private void compensatePreviousSteps(DeleteMemberSagaState state,
                                         List<DeleteMemberSagaStep> steps,
                                         DeleteMemberSagaStep failedStep) {
        Instant now = clock.now();
        // Lặp ngược để bù trừ LIFO: bước cao nhất (gần bước thất bại nhất) được bù trước
        for (int i = failedStep.sequenceNo() - 1; i >= 1; i--) {
            int seq = i;
            steps.stream().filter(s -> s.sequenceNo() == seq).findFirst()
                    .ifPresent(prev -> {
                        // Bỏ qua nếu bước không compensatable, chưa ACK hoặc đã qua irreversible boundary
                        if (prev.compensatable()
                                && prev.state() == DeleteMemberSagaStep.State.ACK
                                && !passedIrreversibleBoundary(state, prev)) {
                            UUID token = UUID.randomUUID();
                            Instant stepDeadline = now.plusSeconds(30);
                            // Cập nhật có điều kiện: chỉ dispatch khi giành được quyền
                            if (sagaRepo.tryClaimCompensation(prev.operationId(), prev.sequenceNo(),
                                    token, now, stepDeadline)) {
                                prev.markCompensationDispatched(now, token, stepDeadline);
                                sagaRepo.updateStep(prev);
                                gateway.stageCompensation(state, prev);
                            }
                        }
                    });
        }
    }

    /** Đồng hồ tiêm được cho use case. */
    public interface Clock { Instant now(); }
}