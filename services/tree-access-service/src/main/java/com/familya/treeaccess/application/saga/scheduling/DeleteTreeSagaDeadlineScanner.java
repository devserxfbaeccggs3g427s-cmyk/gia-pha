package com.familya.treeaccess.application.saga.scheduling;

import com.familya.treeaccess.adapter.in.kafka.DeleteTreeSagaDeadLetterStore;
import com.familya.treeaccess.application.port.out.DeleteTreeSagaGateway;
import com.familya.treeaccess.application.port.out.DeleteTreeSagaRepository;
import com.familya.treeaccess.application.saga.config.SagaDeadlineProperties;
import com.familya.treeaccess.application.saga.config.SagaProperties;
import com.familya.treeaccess.application.saga.retry.SagaClock;
import com.familya.treeaccess.application.saga.retry.SagaRetryPolicy;
import com.familya.treeaccess.domain.model.DeleteTreeSagaState;
import com.familya.treeaccess.domain.model.DeleteTreeSagaStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Deadline scanner cho Saga delete-tree. Được kích hoạt định kỳ bởi
 * {@link Scheduled} để xử lý ba nhóm tác vụ:
 *
 * <ol>
 *   <li>Saga đang hoạt động nhưng đã vượt deadline → phát lệnh bù hoặc chuyển sang {@code MANUAL_REVIEW}.</li>
 *   <li>Bước đã gửi đi nhưng quá hạn ACK → retry hoặc escalate.</li>
 *   <li>Bước ở trạng thái FAILED đã tới {@code nextAttemptAt} → dispatch lại.</li>
 * </ol>
 */
@Component
@ConditionalOnProperty(name = "familya.treeauth.saga.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class DeleteTreeSagaDeadlineScanner {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteTreeSagaDeadlineScanner.class);

    /** Mã lỗi khi một bước vượt quá deadline. */
    static final String FAILURE_STEP_TIMEOUT = "STEP_TIMEOUT";
    /** Mã lỗi khi toàn bộ Saga đã vượt deadline. */
    static final String FAILURE_OPERATION_DEADLINE_EXCEEDED = "OPERATION_DEADLINE_EXCEEDED";
    /** Mã lỗi khi đã retry cạn kiệt. */
    static final String FAILURE_RETRY_EXHAUSTED = "RETRY_EXHAUSTED";
    /** Mã lỗi khi compensation retry cạn kiệt. */
    static final String FAILURE_COMPENSATION_RETRY_EXHAUSTED = "COMPENSATION_RETRY_EXHAUSTED";
    /** Mã lỗi khi có xung đột khi giành quyền dispatch. */
    static final String FAILURE_DISPATCH_CLAIM_CONFLICT = "DISPATCH_CLAIM_CONFLICT";

    /** Định tuyến lỗi → kiểm duyệt thủ công. */
    static final String FAILURE_ROUTING_MANUAL_REVIEW = "MANUAL_REVIEW";
    /** Định tuyến lỗi → đang chạy compensation. */
    static final String FAILURE_ROUTING_COMPENSATING = "COMPENSATING";

    /** Tên service sở hữu cây (dùng để phân biệt bước nội bộ với bước của tham gia viên). */
    static final String OWNER_SERVICE = "tree-access-service";

    /** Kho lưu trữ Saga. */
    private final DeleteTreeSagaRepository sagaRepo;
    /** Gateway để stage sự kiện vòng đời Saga lên outbox. */
    private final DeleteTreeSagaGateway gateway;
    /** Kho dead-letter. */
    private final DeleteTreeSagaDeadLetterStore deadLetterStore;
    /** Đồng hồ tiêm được. */
    private final SagaClock clock;
    /** Chính sách retry + jitter. */
    private final SagaRetryPolicy retryPolicy;
    /** Thuộc tính deadline (lấy từ {@link SagaProperties}). */
    private final SagaDeadlineProperties deadline;

    /**
     * Khởi tạo scanner.
     *
     * @param sagaRepo        kho lưu trữ Saga
     * @param gateway         gateway outbox
     * @param deadLetterStore kho dead-letter
     * @param clock           đồng hồ
     * @param retryPolicy     chính sách retry
     * @param props           thuộc tính Saga
     */
    public DeleteTreeSagaDeadlineScanner(DeleteTreeSagaRepository sagaRepo,
                                         DeleteTreeSagaGateway gateway,
                                         DeleteTreeSagaDeadLetterStore deadLetterStore,
                                         SagaClock clock,
                                         SagaRetryPolicy retryPolicy,
                                         SagaProperties props) {
        this.sagaRepo = sagaRepo;
        this.gateway = gateway;
        this.deadLetterStore = deadLetterStore;
        this.clock = clock;
        this.retryPolicy = retryPolicy;
        this.deadline = props.getDeadline();
    }

    /**
     * Vòng quét deadline. Chạy theo lịch {@code fixedDelay} lấy từ thuộc tính
     * {@code familya.treeauth.saga.deadline.scan-interval-ms}. Mỗi nhóm xử lý
     * trong một batch riêng để giảm ảnh hưởng khi một thao tác lỗi.
     */
    @Scheduled(fixedDelayString = "${familya.treeauth.saga.deadline.scan-interval-ms:60000}")
    public void scan() {
        Instant now = clock.now();
        int batch = deadline.getBatchSize();
        for (DeleteTreeSagaState op : listExpiredOperations(now, batch)) {
            try { processOperation(op, now); }
            catch (RuntimeException e) { LOG.warn("DeadlineScanner processOperation failed op={} err={}", op.operationId(), e.toString()); }
        }
        for (DeleteTreeSagaStep step : sagaRepo.listTimedOutSteps(now, batch)) {
            try { processStepTimeout(step, now); }
            catch (RuntimeException e) { LOG.warn("DeadlineScanner processStepTimeout failed op={} seq={} err={}", step.operationId(), step.sequenceNo(), e.toString()); }
        }
        for (DeleteTreeSagaStep step : sagaRepo.listRetryableSteps(now, batch)) {
            try { processRetry(step, now); }
            catch (RuntimeException e) { LOG.warn("DeadlineScanner processRetry failed op={} seq={} err={}", step.operationId(), step.sequenceNo(), e.toString()); }
        }
    }

    /**
     * Lấy danh sách Saga quá hạn có giới hạn bởi {@code batch}.
     *
     * @param now   thời điểm hiện tại
     * @param batch số bản ghi tối đa
     * @return danh sách trạng thái Saga quá hạn
     */
    private List<DeleteTreeSagaState> listExpiredOperations(Instant now, int batch) {
        return sagaRepo.listActivePastDeadline().stream().limit(batch).toList();
    }

    /**
     * Xử lý một Saga đã vượt deadline. Nếu có thể bù (vẫn còn bước đã ACK chưa
     * vượt rào chắn không thể đảo ngược) sẽ chuyển sang trạng thái COMPENSATING và
     * gửi compensation; ngược lại chuyển sang MANUAL_REVIEW.
     *
     * @param op trạng thái Saga bị quá hạn
     * @param now thời điểm hiện tại
     */
    @Transactional
    public void processOperation(DeleteTreeSagaState op, Instant now) {
        var current = sagaRepo.findState(op.operationId()).orElse(null);
        if (current == null || current.state().isTerminal()) return;
        if (current.state() == DeleteTreeSagaState.State.COMPENSATING) return;
        var steps = sagaRepo.listSteps(current.operationId());
        boolean canCompensate = hasCompensatableAckedStep(current, steps);
        if (canCompensate && !passedIrreversibleBoundary(current)) {
            // Có thể bù: ghi nhận failure, chuyển trạng thái, stage sự kiện và gửi compensation cho các bước đã ACK.
            current.recordFailure(FAILURE_OPERATION_DEADLINE_EXCEEDED,
                    "Operation deadline exceeded; compensating", now);
            if (current.state() != DeleteTreeSagaState.State.COMPENSATING) {
                current.transitionTo(DeleteTreeSagaState.State.COMPENSATING, now);
            }
            sagaRepo.saveState(current);
            gateway.stageOperationStateChanged(current, FAILURE_ROUTING_COMPENSATING);
            compensatePreviousSteps(current, steps, findLatestAckedStep(steps));
            return;
        }
        // Không thể bù an toàn → đưa sang MANUAL_REVIEW để con người xử lý.
        sagaRepo.markOperationManualReview(current.operationId(),
                FAILURE_OPERATION_DEADLINE_EXCEEDED,
                "Operation deadline exceeded without rollback path", now);
        var refreshed = sagaRepo.findState(current.operationId()).orElse(current);
        gateway.stageOperationStateChanged(refreshed, FAILURE_ROUTING_MANUAL_REVIEW);
    }

    /**
     * Xử lý một bước đã gửi đi nhưng vượt deadline ACK. Có ba nhánh:
     *
     * <ul>
     *   <li>Còn lượt retry → lên lịch retry.</li>
     *   <li>Bước của owner hoặc không bù được hoặc đã qua rào chắn → MANUAL_REVIEW.</li>
     *   <li>Đang compensating và retry compensation → escalate MANUAL_REVIEW.</li>
     *   <li>Còn lại → claim compensation và stage message bù.</li>
     * </ul>
     *
     * @param stepRow dòng bước bị timeout
     * @param now    thời điểm hiện tại
     */
    @Transactional
    public void processStepTimeout(DeleteTreeSagaStep stepRow, Instant now) {
        var step = sagaRepo.listSteps(stepRow.operationId()).stream()
                .filter(s -> s.sequenceNo() == stepRow.sequenceNo())
                .findFirst().orElse(null);
        if (step == null || step.state() != DeleteTreeSagaStep.State.DISPATCHED) return;
        var state = sagaRepo.findState(step.operationId()).orElse(null);
        if (state == null || state.state().isTerminal()) return;

        if (step.attemptCount() < step.maxAttempts()) {
            Instant next = retryPolicy.nextAttemptAt(now, step.attemptCount() + 1, state.deadlineAt());
            boolean released = sagaRepo.releaseOrScheduleRetry(step.operationId(), step.sequenceNo(), now, next,
                    FAILURE_STEP_TIMEOUT, "Step timed out; rescheduled");
            if (!released) {
                LOG.info("releaseOrScheduleRetry no-op for op={} seq={} (step state changed)",
                        step.operationId(), step.sequenceNo());
            }
            return;
        }

        if (OWNER_SERVICE.equals(step.participantService())
                || !step.compensatable()
                || passedIrreversibleBoundary(state)) {
            escalateManualReview(step, state, now,
                    FAILURE_RETRY_EXHAUSTED, "Retry exhausted past irreversible boundary");
            return;
        }

        if (state.state() == DeleteTreeSagaState.State.COMPENSATING) {
            escalateManualReview(step, state, now,
                    FAILURE_COMPENSATION_RETRY_EXHAUSTED,
                    "Compensation retry exhausted for " + step.stepCode());
            return;
        }

        UUID token = UUID.randomUUID();
        Instant stepDeadline = now.plusMillis(deadline.getStepTimeoutMs());
        boolean claimed = sagaRepo.tryClaimCompensation(step.operationId(), step.sequenceNo(),
                token, now, stepDeadline);
        if (!claimed) {
            return;
        }
        step.dispatch(now);
        sagaRepo.updateStep(step);
        gateway.stageCompensation(state, step);
        state.recordFailure(FAILURE_RETRY_EXHAUSTED,
                "Retry exhausted for " + step.stepCode(), now);
        boolean stateChanged = state.state() != DeleteTreeSagaState.State.COMPENSATING;
        if (stateChanged) {
            state.transitionTo(DeleteTreeSagaState.State.COMPENSATING, now);
        }
        sagaRepo.saveState(state);
        if (stateChanged) {
            gateway.stageOperationStateChanged(state, FAILURE_ROUTING_COMPENSATING);
        }
        deadLetterStore.saveRetryExhausted(state.operationId(), step.participantService(),
                step.stepCode(), step.attemptCount(),
                new IllegalStateException("Retry exhausted: " + FAILURE_RETRY_EXHAUSTED));
    }

    /**
     * Đẩy một bước và Saga của nó sang MANUAL_REVIEW, đồng thời ghi nhận
     * vào dead-letter để vận hành điều tra.
     *
     * @param step          bước bị lỗi
     * @param state         trạng thái Saga chứa bước
     * @param now           thời điểm hiện tại
     * @param failureCode   mã lỗi để phân loại
     * @param failureMessage thông điệp lỗi
     */
    private void escalateManualReview(DeleteTreeSagaStep step, DeleteTreeSagaState state,
                                      Instant now, String failureCode, String failureMessage) {
        step.markFailed(failureCode, failureMessage, now);
        sagaRepo.updateStep(step);
        sagaRepo.markOperationManualReview(state.operationId(), failureCode, failureMessage, now);
        var refreshed = sagaRepo.findState(state.operationId()).orElse(state);
        gateway.stageOperationStateChanged(refreshed, FAILURE_ROUTING_MANUAL_REVIEW);
        deadLetterStore.saveRetryExhausted(state.operationId(), step.participantService(),
                step.stepCode(), step.attemptCount(),
                new IllegalStateException(failureCode + ": " + failureMessage));
    }

    /**
     * Xử lý một bước đang ở trạng thái FAILED nhưng đã tới {@code nextAttemptAt}.
     * Nếu còn trong deadline của Saga thì claim dispatch và gửi lại; nếu sắp
     * quá deadline thì chuyển sang MANUAL_REVIEW.
     *
     * @param stepRow bước đến hạn retry
     * @param now    thời điểm hiện tại
     */
    @Transactional
    public void processRetry(DeleteTreeSagaStep stepRow, Instant now) {
        var state = sagaRepo.findState(stepRow.operationId()).orElse(null);
        if (state == null || state.state().isTerminal()) return;
        var step = sagaRepo.listSteps(state.operationId()).stream()
                .filter(s -> s.sequenceNo() == stepRow.sequenceNo())
                .findFirst().orElse(null);
        if (step == null || step.state() != DeleteTreeSagaStep.State.FAILED) return;

        if (!now.isBefore(state.deadlineAt())) {
            sagaRepo.markOperationManualReview(state.operationId(),
                    FAILURE_OPERATION_DEADLINE_EXCEEDED,
                    "Retry would exceed operation deadline", now);
            var refreshed = sagaRepo.findState(state.operationId()).orElse(state);
            gateway.stageOperationStateChanged(refreshed, FAILURE_ROUTING_MANUAL_REVIEW);
            return;
        }

        UUID token = UUID.randomUUID();
        Instant stepDeadlineAt = now.plusMillis(deadline.getStepTimeoutMs());
        boolean claimed = sagaRepo.tryClaimDispatch(step.operationId(), step.sequenceNo(), token, now, stepDeadlineAt);
        if (!claimed) {
            Instant next = retryPolicy.nextAttemptAt(now, step.attemptCount() + 1, state.deadlineAt());
            sagaRepo.releaseOrScheduleRetry(step.operationId(), step.sequenceNo(), now, next,
                    FAILURE_DISPATCH_CLAIM_CONFLICT, "Dispatch claim conflict; rescheduled");
            return;
        }

        if (state.state() == DeleteTreeSagaState.State.COMPENSATING) {
            gateway.stageCompensation(state, step);
        } else {
            gateway.stageFirstStep(state, step);
        }
    }

    /**
     * Kiểm tra Saga còn bước bù được không (ACK, compensatable, không phải owner,
     * và chưa qua rào chắn không thể đảo ngược).
     *
     * @param state trạng thái Saga
     * @param steps danh sách các bước
     * @return {@code true} nếu vẫn có thể bù
     */
    private boolean hasCompensatableAckedStep(DeleteTreeSagaState state, List<DeleteTreeSagaStep> steps) {
        boolean irreversiblePassed = passedIrreversibleBoundary(state);
        for (DeleteTreeSagaStep s : steps) {
            if (OWNER_SERVICE.equals(s.participantService())) continue;
            if (s.compensatable() && s.state() == DeleteTreeSagaStep.State.ACK
                    && !irreversiblePassed) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tìm bước ACK có {@code sequenceNo} lớn nhất.
     *
     * @param steps danh sách bước
     * @return bước ACK mới nhất hoặc {@code null}
     */
    private DeleteTreeSagaStep findLatestAckedStep(List<DeleteTreeSagaStep> steps) {
        return steps.stream()
                .filter(s -> s.state() == DeleteTreeSagaStep.State.ACK)
                .reduce((a, b) -> a.sequenceNo() > b.sequenceNo() ? a : b)
                .orElse(null);
    }

    /**
     * @param state trạng thái Saga
     * @return {@code true} nếu Saga đã qua rào chắn không thể đảo ngược
     */
    private boolean passedIrreversibleBoundary(DeleteTreeSagaState state) {
        return state.irreversibleAt() != null;
    }

    /**
     * Gửi compensation cho các bước trước bước biên (bao gồm cả biên nếu được
     * chỉ định). Mỗi bước phải là ACK, compensatable, không phải owner, và chưa
     * qua rào chắn.
     *
     * @param state         trạng thái Saga
     * @param steps         danh sách bước
     * @param boundaryStep  bước biên trên (sequenceNo) hoặc null để bù toàn bộ
     */
    private void compensatePreviousSteps(DeleteTreeSagaState state,
                                         List<DeleteTreeSagaStep> steps,
                                         DeleteTreeSagaStep boundaryStep) {
        Instant now = clock.now();
        int upper = boundaryStep == null ? steps.size() : boundaryStep.sequenceNo();
        for (int i = upper; i >= 1; i--) {
            int seq = i;
            steps.stream().filter(s -> s.sequenceNo() == seq).findFirst()
                    .ifPresent(prev -> {
                        if (!OWNER_SERVICE.equals(prev.participantService())
                                && prev.compensatable()
                                && prev.state() == DeleteTreeSagaStep.State.ACK
                                && !passedIrreversibleBoundary(state)) {
                            UUID token = UUID.randomUUID();
                            Instant stepDeadline = now.plusMillis(deadline.getStepTimeoutMs());
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
}
