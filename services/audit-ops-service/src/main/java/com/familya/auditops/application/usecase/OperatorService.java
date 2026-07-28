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
 * Operator-facing actions. Authorisation requires the
 * {@code AUDIT_OPERATOR} role (Spring Security). The audit log is the
 * source of truth for who-did-what; the projection is not business
 * authority.
 */
@Service
public class OperatorService {

    private static final Logger LOG = LoggerFactory.getLogger(OperatorService.class);

    private final SagaOrchestrator orchestrator;
    private final OperationRepository operationRepo;
    private final AuditAppender audit;
    private final OperationEventBus eventBus;
    private final Clock clock;
    private final List<String> adminRoles;

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

    @Transactional
    public void retry(OperatorRetryCommand cmd, List<String> operatorRoles) {
        requireOperator(operatorRoles);
        Operation op = operationRepo.findById(cmd.operationId())
                .orElseThrow(() -> new OperationNotFoundException(cmd.operationId().toString()));
        LOG.info("Operator retry operation={} actor={} reason={}", op.id(), cmd.operatorUserId(), cmd.reason());
        orchestrator.retry(op.id(), cmd.operatorUserId(), cmd.reason());
    }

    @Transactional
    public void cancel(CancelOperationCommand cmd, List<String> operatorRoles) {
        requireOperator(operatorRoles);
        Operation op = operationRepo.findById(cmd.operationId())
                .orElseThrow(() -> new OperationNotFoundException(cmd.operationId().toString()));
        Instant now = Instant.now(clock);
        if (op.status().isTerminal() && op.status() != OperationStatus.MANUAL_REVIEW) {
            throw new com.familya.auditops.domain.exception.SagaConflictException(
                    "Operation " + op.id() + " is already terminal (" + op.status() + ")");
        }
        SagaTransitions.requireAllowed(op.status(), OperationStatus.MANUAL_REVIEW);
        operationRepo.transition(op.id(), op.version(),
                OperationStatus.MANUAL_REVIEW, "operator.cancel",
                cmd.reason(), now);
        audit.append(new AuditEvent(
                UUID.randomUUID(), op.id(), op.correlationId(),
                cmd.operatorUserId(), AuditEvent.ActorKind.OPERATOR,
                "operation.cancel",
                "operation", op.id().toString(),
                Map.of("reason", cmd.reason()), now, null));
        eventBus.publish(new OperationAdvanced(op.id(), op.status().name(), "MANUAL_REVIEW",
                cmd.operatorUserId().toString(), now),
                Map.of("operationId", op.id().toString(),
                        "correlationId", op.correlationId() == null ? "" : op.correlationId().toString()));
    }

    private void requireOperator(List<String> operatorRoles) {
        if (operatorRoles == null || operatorRoles.isEmpty()
                || operatorRoles.stream().noneMatch(adminRoles::contains)) {
            throw new OperatorNotAuthorizedException(
                    "Operator action requires one of " + adminRoles);
        }
    }
}