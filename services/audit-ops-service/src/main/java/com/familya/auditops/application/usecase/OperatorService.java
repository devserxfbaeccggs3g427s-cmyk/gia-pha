/**
 * Service xử lý các hành động vận hành của operator.
 *
 * <p>Phân quyền yêu cầu role {@code AUDIT_OPERATOR} (qua Spring
 * Security). Audit log là nguồn dữ liệu sự thật cho who-did-what;
 * projection không phải cơ quan có thẩm quyền nghiệp vụ.</p>
 */
package com.familya.auditops.application.usecase;

import com.familya.auditops.application.port.in.CancelOperationCommand;
import com.familya.auditops.application.port.in.OperatorRetryCommand;
import com.familya.auditops.application.port.out.AuditAppender;
import com.familya.auditops.application.port.out.OperationEventBus;
import com.familya.auditops.application.port.out.OperationRepository;
import com.familya.auditops.domain.exception.OperatorNotAuthorizedException;
import com.familya.auditops.domain.exception.OperationNotFoundException;
import com.familya.auditops.domain.event.OperationAdvanced;
import com.familya.auditops.domain.model.AuditEvent;
import com.familya.auditops.domain.model.Operation;
import com.familya.auditops.domain.model.OperationStatus;
import com.familya.auditops.domain.model.SagaTransitions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lớp service thực thi các hành động retry/cancel của operator.
 */
@Service
public class OperatorService {

    /** Logger ghi log thông tin hành động operator. */
    private static final Logger LOG = LoggerFactory.getLogger(OperatorService.class);

    /** Orchestrator để xử lý retry. */
    private final SagaOrchestrator orchestrator;
    /** Repository operation. */
    private final OperationRepository operationRepo;
    /** Port ghi audit. */
    private final AuditAppender audit;
    /** Port publish event. */
    private final OperationEventBus eventBus;
    /** Clock. */
    private final Clock clock;
    /** Danh sách role được phép thao tác. */
    private final List<String> adminRoles;

    /**
     * Khởi tạo service.
     *
     * @param orchestrator orchestrator Saga
     * @param operationRepo repository operation
     * @param audit         port ghi audit
     * @param eventBus      port publish event
     * @param clock         clock
     * @param adminRoles    danh sách role được phép (mặc định
     *                      AUDIT_OPERATOR, PLATFORM)
     */
    public OperatorService(SagaOrchestrator orchestrator,
                           OperationRepository operationRepo,
                           AuditAppender audit,
                           OperationEventBus eventBus,
                           Clock clock,
                           @Value("${familya.auditops.operator.admin-roles:AUDIT_OPERATOR,PLATFORM}") List<String> adminRoles) {
        this.orchestrator = orchestrator;
        this.operationRepo = operationRepo;
        this.audit = audit;
        this.eventBus = eventBus;
        this.clock = clock;
        this.adminRoles = adminRoles;
    }

    /**
     * Retry một operation.
     *
     * <p>Các bước:</p>
     * <ol>
     *   <li>Kiểm tra quyền của operator.</li>
     *   <li>Tìm operation; ném {@link OperationNotFoundException} nếu không tồn tại.</li>
     *   <li>Log hành động và uỷ thác cho orchestrator.</li>
     * </ol>
     *
     * @param cmd command retry
     * @param operatorRoles danh sách role của operator
     * @throws OperatorNotAuthorizedException nếu operator không có role phù hợp
     * @throws OperationNotFoundException nếu operation không tồn tại
     */
    @Transactional
    public void retry(OperatorRetryCommand cmd, List<String> operatorRoles) {
        requireOperator(operatorRoles);
        Operation op = operationRepo.findById(cmd.operationId())
                .orElseThrow(() -> new OperationNotFoundException(cmd.operationId().toString()));
        LOG.info("Operator retry operation={} actor={} reason={}", op.id(), cmd.operatorUserId(), cmd.reason());
        orchestrator.retry(op.id(), cmd.operatorUserId(), cmd.reason());
    }

    /**
     * Huỷ một operation chưa kết thúc.
     *
     * <p>Các bước:</p>
     * <ol>
     *   <li>Kiểm tra quyền của operator.</li>
     *   <li>Tìm operation.</li>
     *   <li>Nếu operation đã ở trạng thái terminal (trừ MANUAL_REVIEW), ném exception.</li>
     *   <li>Kiểm tra transition được phép bằng {@link SagaTransitions}.</li>
     *   <li>Chuyển trạng thái sang {@code MANUAL_REVIEW} với optimistic concurrency.</li>
     *   <li>Ghi audit event.</li>
     *   <li>Publish {@link OperationAdvanced}.</li>
     * </ol>
     *
     * @param cmd command cancel
     * @param operatorRoles danh sách role của operator
     * @throws OperatorNotAuthorizedException nếu thiếu role
     * @throws OperationNotFoundException nếu operation không tồn tại
     * @throws com.familya.auditops.domain.exception.SagaConflictException nếu operation đã terminal
     */
    @Transactional
    public void cancel(CancelOperationCommand cmd, List<String> operatorRoles) {
        requireOperator(operatorRoles);
        Operation op = operationRepo.findById(cmd.operationId())
                .orElseThrow(() -> new OperationNotFoundException(cmd.operationId().toString()));
        Instant now = Instant.now(clock);
        // Không cho phép huỷ operation đã terminal (trừ MANUAL_REVIEW vẫn có thể huỷ).
        if (op.status().isTerminal() && op.status() != OperationStatus.MANUAL_REVIEW) {
            throw new com.familya.auditops.domain.exception.SagaConflictException(
                    "Operation " + op.id() + " is already terminal (" + op.status() + ")");
        }
        // Kiểm tra transition có hợp lệ theo state machine hay không.
        SagaTransitions.requireAllowed(op.status(), OperationStatus.MANUAL_REVIEW);
        operationRepo.transition(op.id(), op.version(),
                OperationStatus.MANUAL_REVIEW, "operator.cancel",
                cmd.reason(), now);
        // Ghi audit event cho hành động cancel.
        audit.append(new AuditEvent(
                UUID.randomUUID(), op.id(), op.correlationId(),
                cmd.operatorUserId(), AuditEvent.ActorKind.OPERATOR,
                "operation.cancel",
                "operation", op.id().toString(),
                Map.of("reason", cmd.reason()), now, null));
        // Publish OperationAdvanced để bounded context khác cập nhật projection.
        eventBus.publish(new OperationAdvanced(op.id(), op.status().name(), "MANUAL_REVIEW",
                cmd.operatorUserId().toString(), now),
                Map.of("operationId", op.id().toString(),
                        "correlationId", op.correlationId() == null ? "" : op.correlationId().toString()));
    }

    /**
     * Kiểm tra operator có ít nhất một role trong {@link #adminRoles}.
     *
     * @param operatorRoles danh sách role của operator (từ Authentication)
     * @throws OperatorNotAuthorizedException nếu không khớp role nào
     */
    private void requireOperator(List<String> operatorRoles) {
        if (operatorRoles == null || operatorRoles.isEmpty()
                || operatorRoles.stream().noneMatch(adminRoles::contains)) {
            throw new OperatorNotAuthorizedException(
                    "Operator action requires one of " + adminRoles);
        }
    }
}