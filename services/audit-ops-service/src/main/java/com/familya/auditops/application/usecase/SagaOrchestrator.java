/**
 * Orchestrator Saga.
 *
 * <p>Nhận phản hồi của participant (qua Kafka), điều khiển máy trạng
 * thái operation tiến triển và phát command compensation khi một
 * participant thất bại. Orchestrator đảm bảo:</p>
 *
 * <ul>
 *   <li>Optimistic concurrency cho mọi transition của operation và step.</li>
 *   <li>Target-revision completion: operation chỉ chuyển sang
 *       {@link OperationStatus#SUCCEEDED} khi mọi step bắt buộc đã
 *       báo cáo target revision/epoch &gt;= target của operation.</li>
 *   <li>Retry policy với bounded jitter; các message poison sẽ được
 *       đưa vào bảng {@code saga_dead_letter} và operation chuyển
 *       sang {@link OperationStatus#MANUAL_REVIEW}.</li>
 *   <li>Compensation boundaries: chỉ những step chưa vượt qua ranh
 *       giới không thể đảo ngược (irreversible) mới được compensate.
 *       Các vi phạm ranh giới sẽ được chuyển sang {@code MANUAL_REVIEW}.</li>
 * </ul>
 *
 * <p>Orchestrator không tham gia vào giao dịch chéo service; mọi
 * thay đổi trạng thái là cục bộ và phát ra một outbox row
 * (Task 13 / ADR-003 / ADR-007).</p>
 */
package com.familya.auditops.application.usecase;

import com.familya.auditops.application.port.in.RecordParticipantReplyCommand;
import com.familya.auditops.application.port.out.AuditAppender;
import com.familya.auditops.application.port.out.OperationEventBus;
import com.familya.auditops.application.port.out.OperationRepository;
import com.familya.auditops.application.port.out.SagaCommandBus;
import com.familya.auditops.application.port.out.SagaStateRepository;
import com.familya.auditops.domain.event.OperationAdvanced;
import com.familya.auditops.domain.exception.OperationNotFoundException;
import com.familya.auditops.domain.exception.SagaConflictException;
import com.familya.auditops.domain.model.AuditEvent;
import com.familya.auditops.domain.model.Operation;
import com.familya.auditops.domain.model.OperationStatus;
import com.familya.auditops.domain.model.SagaStep;
import com.familya.auditops.domain.model.SagaTransitions;
import com.familya.auditops.domain.model.StepStatus;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lớp service đóng vai trò orchestrator Saga.
 */
@Service
public class SagaOrchestrator {

    /** Logger cho orchestrator. */
    private static final Logger LOG = LoggerFactory.getLogger(SagaOrchestrator.class);

    /** Repository operation. */
    private final OperationRepository operationRepo;
    /** Repository Saga. */
    private final SagaStateRepository sagaRepo;
    /** Port publish command cho participant. */
    private final SagaCommandBus commandBus;
    /** Port ghi audit. */
    private final AuditAppender audit;
    /** Port publish event. */
    private final OperationEventBus eventBus;
    /** Metric collector. */
    private final PlatformMetrics metrics;
    /** Clock. */
    private final Clock clock;
    /** Số lần retry tối đa cho một step. */
    private final int maxAttempts;
    /** Backoff cơ sở (ms). */
    private final long retryBaseMs;
    /** Backoff tối đa (ms). */
    private final long retryMaxMs;

    /**
     * Khởi tạo orchestrator với các phụ thuộc và tham số retry.
     *
     * @param operationRepo repository operation
     * @param sagaRepo      repository Saga
     * @param commandBus    port publish command
     * @param audit         port ghi audit
     * @param eventBus      port publish event
     * @param metrics       metric collector
     * @param clock         clock
     * @param maxAttempts   số lần retry tối đa (mặc định 5)
     * @param retryBaseMs   backoff cơ sở (mặc định 500)
     * @param retryMaxMs    backoff tối đa (mặc định 30000)
     */
    public SagaOrchestrator(OperationRepository operationRepo,
                            SagaStateRepository sagaRepo,
                            SagaCommandBus commandBus,
                            AuditAppender audit,
                            OperationEventBus eventBus,
                            PlatformMetrics metrics,
                            Clock clock,
                            @Value("${familya.auditops.saga.max-attempts:5}") int maxAttempts,
                            @Value("${familya.auditops.saga.retry-base-ms:500}") long retryBaseMs,
                            @Value("${familya.auditops.saga.retry-max-ms:30000}") long retryMaxMs) {
        this.operationRepo = operationRepo;
        this.sagaRepo = sagaRepo;
        this.commandBus = commandBus;
        this.audit = audit;
        this.eventBus = eventBus;
        this.metrics = metrics;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.retryBaseMs = retryBaseMs;
        this.retryMaxMs = retryMaxMs;
    }

    /**
     * Áp dụng phản hồi của participant.
     *
     * <p>Phản hồi <b>BẮT BUỘC</b> đã được dedup bởi consumer thông qua
     * inbox; phương thức này tin tưởng consumer và áp dụng trực tiếp
     * vào máy trạng thái cục bộ.</p>
     *
     * @param reply command phản hồi từ participant
     */
    @Transactional
    public void applyReply(RecordParticipantReplyCommand reply) {
        Operation op = operationRepo.findById(reply.operationId())
                .orElseThrow(() -> new OperationNotFoundException(reply.operationId().toString()));
        Instant now = Instant.now(clock);

        // Nếu operation đã terminal (trừ MANUAL_REVIEW) thì bỏ qua phản hồi trễ.
        if (op.status().isTerminal() && op.status() != OperationStatus.MANUAL_REVIEW) {
            LOG.debug("Ignoring reply for terminal operation={} status={}",
                    op.id(), op.status());
            return;
        }

        // Phân nhánh theo outcome.
        switch (reply.outcome()) {
            case ACKED -> handleAck(op, reply, now);
            case FAILED -> handleFailure(op, reply, now);
            case COMPENSATED -> handleCompensated(op, reply, now);
            case DEAD_LETTERED -> handleDeadLetter(op, reply, now);
        }
    }

    /**
     * Chuyển operation từ {@code MANUAL_REVIEW} (hoặc {@code COMPENSATING})
     * sang {@code RUNNING}, đồng thời dispatch lại các step chưa ack.
     *
     * <p>Idempotent: step đã ở {@link StepStatus#ACKED} hoặc
     * {@link StepStatus#COMPENSATED} sẽ được bỏ qua.</p>
     *
     * @param operationId    id operation
     * @param operatorUserId id operator
     * @param reason         lý do retry
     */
    @Transactional
    public void retry(UUID operationId, UUID operatorUserId, String reason) {
        Operation op = operationRepo.findById(operationId)
                .orElseThrow(() -> new OperationNotFoundException(operationId.toString()));
        // Chỉ retry khi đang ở MANUAL_REVIEW hoặc COMPENSATING.
        if (!op.status().equals(OperationStatus.MANUAL_REVIEW)
                && !op.status().equals(OperationStatus.COMPENSATING)) {
            throw new SagaConflictException(
                    "Operation " + operationId + " is not eligible for retry (status=" + op.status() + ")");
        }
        Instant now = Instant.now(clock);
        SagaTransitions.requireAllowed(op.status(), OperationStatus.RUNNING);
        Operation advanced = operationRepo.transition(op.id(), op.version(),
                OperationStatus.RUNNING, null, null, now);

        // Duyệt tất cả các step; với mỗi step chưa ack/compensate, dispatch lại.
        for (SagaStep step : sagaRepo.listSteps(op.id())) {
            if (step.status() == StepStatus.ACKED || step.status() == StepStatus.COMPENSATED) {
                continue;
            }
            sagaRepo.transitionStep(op.id(), step.participantService(), step.stepName(),
                    StepStatus.DISPATCHED, null, null, now);
            commandBus.dispatchCommand(buildCommand(advanced, step, now, retryJitterMillis()));
        }

        // Ghi audit event cho hành động retry.
        audit.append(new AuditEvent(
                UUID.randomUUID(), op.id(), op.correlationId(),
                operatorUserId, AuditEvent.ActorKind.OPERATOR,
                "operation.retry",
                "operation", op.id().toString(),
                Map.of("reason", reason == null ? "" : reason),
                now, null));

        // Publish OperationAdvanced để bounded context khác cập nhật.
        eventBus.publish(new OperationAdvanced(op.id(), op.status().name(), "RUNNING",
                operatorUserId == null ? "operator" : operatorUserId.toString(), now),
                correlationHeaders(op));
        metrics.mutationAccepted("audit-ops-service", "saga_retry");
    }

    /**
     * Xử lý phản hồi ACK từ participant.
     *
     * <p>Các bước:</p>
     * <ol>
     *   <li>Kiểm tra target revision; nếu nhỏ hơn target thì đánh FAILED.</li>
     *   <li>Chuyển trạng thái step sang {@link StepStatus#ACKED}.</li>
     *   <li>Ghi audit event.</li>
     *   <li>Nếu tất cả step đã ACK thì chuyển operation sang {@code SUCCEEDED}.</li>
     *   <li>Nếu operation đang ở PENDING thì chuyển sang {@code RUNNING}.</li>
     * </ol>
     */
    private void handleAck(Operation op, RecordParticipantReplyCommand reply, Instant now) {
        // Kiểm tra target revision: nếu participant ack với revision cũ thì coi như stale.
        if (op.targetRevision() != null && reply.ackedRevision() != null
                && reply.ackedRevision() < op.targetRevision()) {
            sagaRepo.transitionStep(op.id(), reply.participantService(), reply.stepName(),
                    StepStatus.FAILED, "projection.stale",
                    "Acked revision " + reply.ackedRevision() + " < target " + op.targetRevision(), now);
            handleFailure(op, withError(reply, "projection.stale",
                    "Acked revision below target"), now);
            return;
        }

        // Đánh dấu step ACKED.
        sagaRepo.transitionStep(op.id(), reply.participantService(), reply.stepName(),
                StepStatus.ACKED, null, null, now);

        // Ghi audit event.
        audit.append(new AuditEvent(
                UUID.randomUUID(), op.id(), op.correlationId(),
                null, AuditEvent.ActorKind.SERVICE,
                "saga.step.acked",
                reply.participantService(), reply.stepName(),
                Map.of("sequenceNo", reply.stepName()), now, null));

        // Nếu tất cả step đã ACK thì operation SUCCEEDED.
        if (allRequiredStepsAcked(op.id())) {
            SagaTransitions.requireAllowed(op.status(), OperationStatus.SUCCEEDED);
            Operation advanced = operationRepo.transition(op.id(), op.version(),
                    OperationStatus.SUCCEEDED, null, null, now);
            eventBus.publish(new OperationAdvanced(op.id(), op.status().name(), "SUCCEEDED",
                    "orchestrator", now), correlationHeaders(op));
            metrics.mutationAcceptedCounter("audit-ops-service", "saga_succeeded").increment();
            LOG.info("Operation {} succeeded", advanced.id());
        } else if (op.status() == OperationStatus.PENDING) {
            // Nếu đang PENDING và có step đầu tiên ACK, chuyển sang RUNNING.
            SagaTransitions.requireAllowed(op.status(), OperationStatus.RUNNING);
            operationRepo.transition(op.id(), op.version(),
                    OperationStatus.RUNNING, null, null, now);
            eventBus.publish(new OperationAdvanced(op.id(), "PENDING", "RUNNING",
                    "orchestrator", now), correlationHeaders(op));
        }
    }

    /**
     * Xử lý phản hồi FAILED.
     *
     * <p>Các bước:</p>
     * <ol>
     *   <li>Tìm step tương ứng; nếu không tồn tại thì ném
     *       {@link SagaConflictException}.</li>
     *   <li>Nếu đã hết retry thì chuyển sang dead-letter.</li>
     *   <li>Đánh dấu step FAILED và dispatch lại command với backoff.</li>
     *   <li>Nếu operation đang PENDING thì chuyển sang RUNNING.</li>
     * </ol>
     */
    private void handleFailure(Operation op, RecordParticipantReplyCommand reply, Instant now) {
        SagaStep step = sagaRepo.listSteps(op.id()).stream()
                .filter(s -> s.participantService().equals(reply.participantService())
                        && s.stepName().equals(reply.stepName()))
                .findFirst()
                .orElseThrow(() -> new SagaConflictException(
                        "Unknown step " + reply.participantService() + "/" + reply.stepName()));

        // Nếu đã đạt số lần retry tối đa, chuyển sang dead-letter.
        if (step.attemptCount() >= maxAttempts) {
            handleDeadLetter(op, reply, now);
            return;
        }

        // Đánh dấu FAILED và dispatch lại command với backoff jitter.
        sagaRepo.transitionStep(op.id(), reply.participantService(), reply.stepName(),
                StepStatus.FAILED,
                reply.errorCode(), reply.errorMessage(), now);
        long backoff = retryJitterMillis();
        commandBus.dispatchCommand(buildCommand(op, step, now, backoff));

        // PENDING → RUNNING khi có lần retry đầu tiên.
        if (op.status() == OperationStatus.PENDING) {
            SagaTransitions.requireAllowed(op.status(), OperationStatus.RUNNING);
            operationRepo.transition(op.id(), op.version(),
                    OperationStatus.RUNNING, null, null, now);
        }
        metrics.mutationFailed("audit-ops-service", "saga_step",
                reply.errorCode() == null ? "unknown" : reply.errorCode());
    }

    /**
     * Xử lý phản hồi COMPENSATED. Nếu tất cả step đã được compensate
     * thì chuyển operation sang {@code COMPENSATED}.
     */
    private void handleCompensated(Operation op, RecordParticipantReplyCommand reply, Instant now) {
        sagaRepo.transitionStep(op.id(), reply.participantService(), reply.stepName(),
                StepStatus.COMPENSATED, null, null, now);
        if (allRequiredStepsCompensated(op.id())) {
            SagaTransitions.requireAllowed(op.status(), OperationStatus.COMPENSATED);
            operationRepo.transition(op.id(), op.version(),
                    OperationStatus.COMPENSATED, null, null, now);
            eventBus.publish(new OperationAdvanced(op.id(), "COMPENSATING", "COMPENSATED",
                    "orchestrator", now), correlationHeaders(op));
        }
    }

    /**
     * Xử lý phản hồi DEAD_LETTERED hoặc khi step đã hết retry.
     *
     * <p>Các bước:</p>
     * <ol>
     *   <li>Chuyển step sang DEAD_LETTERED.</li>
     *   <li>Ghi row dead-letter.</li>
     *   <li>Chuyển operation sang MANUAL_REVIEW.</li>
     *   <li>Ghi audit event và publish {@code OperationQuarantined}.</li>
     * </ol>
     */
    private void handleDeadLetter(Operation op, RecordParticipantReplyCommand reply, Instant now) {
        sagaRepo.transitionStep(op.id(), reply.participantService(), reply.stepName(),
                StepStatus.DEAD_LETTERED, reply.errorCode(), reply.errorMessage(), now);
        sagaRepo.deadLetterStep(op.id(), reply.participantService(), reply.stepName(),
                reply.errorCode(), reply.errorMessage(), Map.of(), now);

        SagaTransitions.requireAllowed(op.status(), OperationStatus.MANUAL_REVIEW);
        operationRepo.transition(op.id(), op.version(),
                OperationStatus.MANUAL_REVIEW,
                reply.errorCode(), reply.errorMessage(), now);
        audit.append(new AuditEvent(
                UUID.randomUUID(), op.id(), op.correlationId(),
                null, AuditEvent.ActorKind.SERVICE,
                "operation.quarantined",
                reply.participantService(), reply.stepName(),
                Map.of("errorCode", reply.errorCode() == null ? "" : reply.errorCode(),
                        "errorMessage", reply.errorMessage() == null ? "" : reply.errorMessage()),
                now, null));
        eventBus.publish(new com.familya.auditops.domain.event.OperationQuarantined(
                op.id(), reply.errorCode() + ": " + reply.errorMessage(), now),
                correlationHeaders(op));
        metrics.mutationFailed("audit-ops-service", "saga_quarantined",
                reply.errorCode() == null ? "unknown" : reply.errorCode());
    }

    /**
     * Kiểm tra tất cả step bắt buộc đã ở trạng thái {@link StepStatus#ACKED}.
     *
     * @param operationId id operation
     * @return true nếu có step và tất cả đã ACK
     */
    private boolean allRequiredStepsAcked(UUID operationId) {
        long total = sagaRepo.listSteps(operationId).size();
        long acked = sagaRepo.countByOperationAndStatus(operationId, StepStatus.ACKED);
        return total > 0 && acked == total;
    }

    /**
     * Kiểm tra tất cả step bắt buộc đã ở trạng thái {@link StepStatus#COMPENSATED}.
     *
     * @param operationId id operation
     * @return true nếu có step và tất cả đã COMPENSATED
     */
    private boolean allRequiredStepsCompensated(UUID operationId) {
        long total = sagaRepo.listSteps(operationId).size();
        long comp = sagaRepo.countByOperationAndStatus(operationId, StepStatus.COMPENSATED);
        return total > 0 && comp == total;
    }

    /**
     * Xây dựng Saga command để dispatch lại cho participant.
     *
     * @param op       operation
     * @param step     step cần dispatch
     * @param now      thời điểm hiện tại
     * @param backoffMs backoff (ms) sẽ được cộng vào deadline
     * @return command envelope
     */
    private SagaCommandBus.SagaCommand buildCommand(Operation op, SagaStep step, Instant now, long backoffMs) {
        Map<String, String> headers = new HashMap<>();
        headers.put("correlationId", op.correlationId() == null ? op.id().toString() : op.correlationId().toString());
        headers.put("operationId", op.id().toString());
        headers.put("stepName", step.stepName());
        return new SagaCommandBus.SagaCommand(
                op.id(), op.correlationId(),
                step.participantService(), step.stepName(), step.sequenceNo(),
                "saga.step.retry",
                "{\"retry\":true}",
                step.expectedVersion(),
                step.targetRevision(),
                step.targetEpoch(),
                now.plusMillis(backoffMs),
                headers);
    }

    /**
     * Tính backoff jitter theo exponential với giới hạn.
     *
     * <p>Công thức:</p>
     * <ol>
     *   <li>{@code base = min(retryBaseMs, retryMaxMs)}.</li>
     *   <li>Nhân đôi {@code base} cho tới {@code cap} hoặc đến {@code maxAttempts}.</li>
     *   <li>Cộng thêm jitter ngẫu nhiên trong [0, exp - base].</li>
     * </ol>
     *
     * @return backoff (ms) được chọn
     */
    private long retryJitterMillis() {
        long cap = Math.max(retryBaseMs, retryMaxMs);
        long base = Math.min(retryBaseMs, retryMaxMs);
        long exp = base;
        for (int i = 1; i < Math.min(6, Math.max(1, maxAttempts)); i++) {
            if (exp >= cap) break;
            exp *= 2;
        }
        long jitter = (long) (Math.random() * (exp - base + 1));
        return Math.min(cap, base + jitter);
    }

    /**
     * Tạo bản sao {@link RecordParticipantReplyCommand} với mã lỗi
     * mới, giữ nguyên các trường còn lại.
     */
    private static RecordParticipantReplyCommand withError(RecordParticipantReplyCommand reply,
                                                            String code, String msg) {
        return new RecordParticipantReplyCommand(
                reply.operationId(), reply.participantService(), reply.stepName(),
                reply.outcome(),
                reply.ackedRevision(), reply.ackedEpoch(),
                reply.expectedVersion(),
                code, msg,
                reply.correlationId(), reply.causationId());
    }

    /**
     * Tạo header correlation cho outbox.
     *
     * @param op operation nguồn
     * @return map header
     */
    private static Map<String, String> correlationHeaders(Operation op) {
        Map<String, String> h = new HashMap<>();
        if (op.correlationId() != null) h.put("correlationId", op.correlationId().toString());
        h.put("operationId", op.id().toString());
        return h;
    }
}