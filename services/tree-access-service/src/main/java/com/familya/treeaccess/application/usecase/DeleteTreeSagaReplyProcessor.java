package com.familya.treeaccess.application.usecase;

import com.familya.treeaccess.adapter.in.kafka.DeleteTreeSagaDeadLetterStore;
import com.familya.treeaccess.application.port.in.DeleteTreeSagaReplyCommand;
import com.familya.treeaccess.application.port.out.DeleteTreeSagaGateway;
import com.familya.treeaccess.application.port.out.DeleteTreeSagaRepository;
import com.familya.treeaccess.application.port.out.TreeEventPublisher;
import com.familya.treeaccess.application.port.out.TreeRepository;
import com.familya.treeaccess.domain.event.TreeAdvancedRevision;
import com.familya.treeaccess.domain.exception.TreeNotFoundException;
import com.familya.treeaccess.domain.model.DeleteTreeSagaState;
import com.familya.treeaccess.domain.model.DeleteTreeSagaStep;
import com.familya.treeaccess.domain.model.Tree;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DeleteTreeSagaReplyProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteTreeSagaReplyProcessor.class);

    /** Kho lưu trữ Saga. */
    private final DeleteTreeSagaRepository sagaRepo;
    /** Gateway stage sự kiện. */
    private final DeleteTreeSagaGateway gateway;
    /** Kho dead-letter. */
    private final DeleteTreeSagaDeadLetterStore deadLetterStore;
    /** Kho lưu trữ cây (dùng khi finalize). */
    private final TreeRepository treeRepo;
    /** Bộ publish sự kiện cây. */
    private final TreeEventPublisher publisher;
    /** Metric giám sát. */
    private final PlatformMetrics metrics;
    /** Đồng hồ tiêm được. */
    private final Clock clock;

    /**
     * Khởi tạo processor.
     *
     * @param sagaRepo        kho lưu trữ Saga
     * @param gateway         gateway outbox
     * @param deadLetterStore kho dead-letter
     * @param treeRepo        kho lưu trữ cây
     * @param publisher       bộ publish sự kiện
     * @param metrics         metric giám sát
     * @param clock           đồng hồ
     */
    public DeleteTreeSagaReplyProcessor(DeleteTreeSagaRepository sagaRepo,
                                        DeleteTreeSagaGateway gateway,
                                        DeleteTreeSagaDeadLetterStore deadLetterStore,
                                        TreeRepository treeRepo,
                                        TreeEventPublisher publisher,
                                        PlatformMetrics metrics,
                                        Clock clock) {
        this.sagaRepo = sagaRepo;
        this.gateway = gateway;
        this.deadLetterStore = deadLetterStore;
        this.treeRepo = treeRepo;
        this.publisher = publisher;
        this.metrics = metrics;
        this.clock = clock;
    }

    /**
     * Xử lý một reply Saga. Phương thức này phân nhánh theo nhiều trường hợp:
     *
     * <ul>
     *   <li>Reply compensation áp dụng → xử lý kết quả compensation.</li>
     *   <li>Forward reply khi Saga đang COMPENSATING → bỏ qua (reply cũ).</li>
     *   <li>Forward reply khi bước không ở DISPATCHED → bỏ qua (stale).</li>
     *   <li>Forward reply thất bại → xử lý lỗi tham gia viên (retry hoặc bù).</li>
     *   <li>Forward reply không đạt barrier → MANUAL_REVIEW.</li>
     *   <li>Forward reply đạt barrier → ACK và dispatch bước tiếp theo, hoặc finalize.</li>
     * </ul>
     *
     * @param cmd lệnh reply Saga
     */
    @Transactional
    public void process(DeleteTreeSagaReplyCommand cmd) {
        Optional<DeleteTreeSagaState> opt = sagaRepo.findState(cmd.operationId());
        if (opt.isEmpty()) {
            LOG.warn("Ignoring reply for unknown delete-tree operationId={}", cmd.operationId());
            return;
        }
        DeleteTreeSagaState state = opt.get();
        if (state.state().isTerminal()) {
            LOG.info("Ignoring reply for terminal delete-tree Saga operationId={} state={}",
                    state.operationId(), state.state());
            return;
        }

        List<DeleteTreeSagaStep> steps = sagaRepo.listSteps(cmd.operationId());
        DeleteTreeSagaStep current = steps.stream()
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
        metrics.mutationAccepted("tree-access-service", "deleteTree.reply");

        if (cmd.compensationApplied()) {
            if (cmd.failed()) {
                handleCompensationFailure(state, current, cmd.failureCode(), cmd.failureMessage(), now);
            } else {
                handleCompensationReply(state, steps, current, now);
            }
            return;
        }
        if (state.state() == DeleteTreeSagaState.State.COMPENSATING) {
            LOG.info("Ignoring forward delete-tree reply while compensating operationId={} step={}",
                    state.operationId(), current.stepCode());
            return;
        }
        if (current.state() != DeleteTreeSagaStep.State.DISPATCHED) {
            LOG.info("Ignoring stale delete-tree reply operationId={} step={} state={}",
                    state.operationId(), current.stepCode(), current.state());
            return;
        }

        if (cmd.failed()) {
            handleParticipantFailure(state, steps, current, cmd.failureCode(), cmd.failureMessage(), now);
            return;
        }

        if (!satisfiesBarrier(state, cmd)) {
            handleBarrierFailure(state, current, now);
            return;
        }

        current.ack(now, cmd.appliedAggregateVersion(), cmd.appliedEpoch());
        sagaRepo.updateStep(current);

        DeleteTreeSagaStep next = nextPendingStep(steps, current.sequenceNo());
        if (next == null || "FINALIZE_TREE_DELETION".equals(next.stepCode())) {
            runFinalize(state, now);
            sagaRepo.saveState(state);
            gateway.stageOperationStateChanged(state);
            return;
        }

        dispatchStep(state, next, now);
        sagaRepo.saveState(state);
        gateway.stageOperationStateChanged(state);
    }

    /**
     * Thực hiện bước FINALIZE_TREE_DELETION: chuyển trạng thái sang FINALIZING,
     * đánh dấu bước cuối là DISPATCHED rồi ACK ngay (vì đây là thao tác nội bộ),
     * sau đó đặt trạng thái cây thành TOMBSTONED và chuyển trạng thái Saga sang
     * SUCCEEDED.
     *
     * @param state trạng thái Saga
     * @param now   thời điểm hiện tại
     */
    private void runFinalize(DeleteTreeSagaState state, Instant now) {
        DeleteTreeSagaStep finalizeStep = sagaRepo.listSteps(state.operationId()).stream()
                .filter(s -> s.stepCode().equals("FINALIZE_TREE_DELETION"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "FINALIZE_TREE_DELETION step missing for operation " + state.operationId()));
        state.transitionTo(DeleteTreeSagaState.State.FINALIZING, now);
        finalizeStep.dispatch(now);
        sagaRepo.updateStep(finalizeStep);
        finalizeTree(state, now);
        finalizeStep.ack(now, state.targetAggregateVersion(), state.targetEpoch());
        sagaRepo.updateStep(finalizeStep);
        state.transitionTo(DeleteTreeSagaState.State.SUCCEEDED, now);
    }

    /**
     * Xử lý khi compensation trả về thành công: đánh dấu bước COMPENSATED và
     * kiểm tra xem tất cả compensation đã hoàn tất để chuyển Saga sang FAILED.
     *
     * @param state   trạng thái Saga
     * @param steps   danh sách bước
     * @param current bước đã nhận compensation reply
     * @param now     thời điểm hiện tại
     */
    private void handleCompensationReply(DeleteTreeSagaState state,
                                         List<DeleteTreeSagaStep> steps,
                                         DeleteTreeSagaStep current,
                                         Instant now) {
        if (state.state() != DeleteTreeSagaState.State.COMPENSATING
                || current.state() != DeleteTreeSagaStep.State.DISPATCHED) {
            return;
        }
        current.compensate(now);
        sagaRepo.updateStep(current);
        boolean complete = sagaRepo.listSteps(state.operationId()).stream()
                .filter(s -> !"tree-access-service".equals(s.participantService()))
                .filter(DeleteTreeSagaStep::compensatable)
                .noneMatch(s -> s.state() == DeleteTreeSagaStep.State.ACK
                        || s.state() == DeleteTreeSagaStep.State.DISPATCHED);
        if (complete) {
            state.transitionTo(DeleteTreeSagaState.State.FAILED, now);
            sagaRepo.saveState(state);
            gateway.stageOperationStateChanged(state);
        }
    }

    /**
     * Xử lý khi compensation thất bại: nếu còn lượt thì gửi lại, ngược lại
     * đánh dấu DEAD_LETTERED và đẩy Saga sang MANUAL_REVIEW.
     *
     * @param state   trạng thái Saga
     * @param current bước bị lỗi compensation
     * @param code    mã lỗi
     * @param message thông điệp lỗi
     * @param now     thời điểm hiện tại
     */
    private void handleCompensationFailure(DeleteTreeSagaState state,
                                           DeleteTreeSagaStep current,
                                           String code, String message,
                                           Instant now) {
        if (state.state() != DeleteTreeSagaState.State.COMPENSATING
                || current.state() != DeleteTreeSagaStep.State.DISPATCHED) return;
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
        state.transitionTo(DeleteTreeSagaState.State.MANUAL_REVIEW, now);
        sagaRepo.saveState(state);
        deadLetterStore.saveRetryExhausted(
                state.operationId(), current.participantService(), current.stepCode(),
                current.attemptCount(), new IllegalStateException("Compensation failed: " + code + " " + message));
        gateway.stageOperationStateChanged(state);
    }

    /**
     * Xử lý khi tham gia viên forward bị lỗi: thử retry nếu còn lượt; nếu hết
     * lượt và chưa qua rào chắn không thể đảo ngược thì chuyển Saga sang
     * COMPENSATING và gọi {@link #compensatePreviousSteps}; ngược lại
     * MANUAL_REVIEW.
     *
     * @param state   trạng thái Saga
     * @param steps   danh sách các bước
     * @param current bước bị lỗi
     * @param code    mã lỗi
     * @param message thông điệp
     * @param now     thời điểm hiện tại
     */
    private void handleParticipantFailure(DeleteTreeSagaState state,
                                          List<DeleteTreeSagaStep> steps,
                                          DeleteTreeSagaStep current,
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
            boolean stateChanged = state.state() != DeleteTreeSagaState.State.COMPENSATING;
            if (stateChanged) {
                state.transitionTo(DeleteTreeSagaState.State.COMPENSATING, now);
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
        state.transitionTo(DeleteTreeSagaState.State.MANUAL_REVIEW, now);
        sagaRepo.saveState(state);
        deadLetterStore.saveRetryExhausted(
                state.operationId(), current.participantService(), current.stepCode(),
                current.attemptCount(),
                new IllegalStateException("Participant retry exhausted: " + code + " " + message));
        gateway.stageOperationStateChanged(state);
    }

    /**
     * Xử lý khi tham gia viên đạt barrier không thoả đáng: đánh dấu bước FAILED
     * và đẩy Saga sang MANUAL_REVIEW (vì barrier là hard requirement).
     *
     * @param state   trạng thái Saga
     * @param current bước đạt barrier nhưng không thoả đáng
     * @param now     thời điểm hiện tại
     */
    private void handleBarrierFailure(DeleteTreeSagaState state,
                                      DeleteTreeSagaStep current, Instant now) {
        String code = "BARRIER_NOT_MET";
        String message = "step " + current.stepCode() + " did not meet barrier";
        current.fail(code, message, now);
        sagaRepo.updateStep(current);
        state.recordFailure(code, message, now);
        state.transitionTo(DeleteTreeSagaState.State.MANUAL_REVIEW, now);
        sagaRepo.saveState(state);
        gateway.stageOperationStateChanged(state);
    }

    /**
     * Gửi lại một bước Saga. Nếu bước đã có dispatchToken hợp lệ (đã claim trước
     * đó) thì chỉ stage message; ngược lại tạo token mới và claim trước khi gửi.
     *
     * @param state trạng thái Saga
     * @param step  bước cần gửi
     * @param now   thời điểm hiện tại
     */
    private void dispatchStep(DeleteTreeSagaState state, DeleteTreeSagaStep step, Instant now) {
        if (step.attemptCount() > 0 && step.dispatchToken() != null && step.state() == DeleteTreeSagaStep.State.DISPATCHED) {
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

    /**
     * @param state trạng thái Saga
     * @param failed bước bị lỗi (tham số để giữ API đồng nhất)
     * @return {@code true} nếu Saga đã qua rào chắn không thể đảo ngược
     */
    private static boolean passedIrreversibleBoundary(DeleteTreeSagaState state, DeleteTreeSagaStep failed) {
        return state.irreversibleAt() != null;
    }

    /**
     * Kiểm tra reply có đạt "barrier" của Saga hay không: appliedAggregateVersion
     * và appliedEpoch phải bằng hoặc cao hơn target.
     *
     * @param state trạng thái Saga
     * @param cmd   reply nhận được
     * @return {@code true} nếu barrier đạt
     */
    private boolean satisfiesBarrier(DeleteTreeSagaState state, DeleteTreeSagaReplyCommand cmd) {
        return cmd.appliedAggregateVersion() >= state.targetAggregateVersion()
                && cmd.appliedEpoch() >= state.targetEpoch();
    }

    /**
     * Tìm bước tiếp theo còn đang PENDING và có {@code sequenceNo > currentSeq}.
     *
     * @param steps     danh sách bước
     * @param currentSeq số thứ tự hiện tại
     * @return bước tiếp theo hoặc {@code null}
     */
    private static DeleteTreeSagaStep nextPendingStep(List<DeleteTreeSagaStep> steps, int currentSeq) {
        return steps.stream()
                .filter(s -> s.sequenceNo() > currentSeq)
                .filter(s -> s.state() == DeleteTreeSagaStep.State.PENDING)
                .findFirst()
                .orElse(null);
    }

    /**
     * Bù các bước trước {@code failedStep}. Mỗi bước phải là ACK, compensatable,
     * không phải owner, và chưa qua rào chắn. Khi claim thành công thì stage lệnh
     * compensation tương ứng.
     *
     * @param state      trạng thái Saga
     * @param steps      danh sách bước
     * @param failedStep bước bị lỗi; các bước trước nó sẽ được bù
     */
    private void compensatePreviousSteps(DeleteTreeSagaState state,
                                         List<DeleteTreeSagaStep> steps,
                                         DeleteTreeSagaStep failedStep) {
        Instant now = clock.now();
        for (int i = failedStep.sequenceNo() - 1; i >= 1; i--) {
            int seq = i;
            steps.stream().filter(s -> s.sequenceNo() == seq).findFirst()
                    .ifPresent(prev -> {
                        if (!"tree-access-service".equals(prev.participantService())
                                && prev.compensatable()
                                && prev.state() == DeleteTreeSagaStep.State.ACK
                                && !passedIrreversibleBoundary(state, prev)) {
                            UUID token = UUID.randomUUID();
                            Instant stepDeadline = now.plusSeconds(30);
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

    /**
     * Hoàn tất xóa cây: chuyển sang trạng thái TOMBSTONED và phát sự kiện
     * {@link TreeAdvancedRevision} để các service khác cập nhật.
     *
     * @param state trạng thái Saga
     * @param now   thời điểm hiện tại
     */
    private void finalizeTree(DeleteTreeSagaState state, Instant now) {
        Tree tree = treeRepo.findTree(state.treeId())
                .orElseThrow(() -> new TreeNotFoundException("Tree " + state.treeId() + " not found"));
        if (tree.isTombstoned()) return;
        tree.tombstone(tree.version());
        treeRepo.updateTree(tree);
        publisher.publishTreeEvent(new TreeAdvancedRevision(
                tree.id(), tree.revision(), tree.epoch(),
                state.operationId(), "delete-tree-saga:finalize", now));
    }

    /** Interface đồng hồ cho processor. */
    public interface Clock { Instant now(); }
}