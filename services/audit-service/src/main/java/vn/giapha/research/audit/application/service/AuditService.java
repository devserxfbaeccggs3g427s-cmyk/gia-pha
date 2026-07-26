package vn.giapha.research.audit.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import vn.giapha.research.audit.application.port.in.RecordAuditUseCase;
import vn.giapha.research.audit.application.port.out.AuditWriter;
import vn.giapha.research.audit.domain.model.BusinessAuditEntry;
import vn.giapha.research.audit.domain.model.SecurityAuditEvent;
import vn.giapha.research.audit.support.Hashes;
import vn.giapha.research.audit.support.Ids;

/**
 * Audit recording service (Task 12.3, Requirement 13.1-13.4).
 *
 * <p>Business rows join the caller's transaction (MANDATORY): commit-atomic
 * with the mutation and the tree revision, and a rollback erases them —
 * Requirement 13.4's "no misleading audit row" comes from transaction
 * semantics, not compensation logic. Security rows commit independently
 * (REQUIRES_NEW) because a blocked login must be recorded even though the
 * surrounding work fails.
 */
@Service
public class AuditService implements RecordAuditUseCase {

    private static final int MAX_USER_AGENT = 400; // security_audit_logs.user_agent

    private final AuditWriter auditWriter;
    private final AuditRedactor redactor;

    public AuditService(AuditWriter auditWriter, AuditRedactor redactor) {
        this.auditWriter = auditWriter;
        this.redactor = redactor;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordBusiness(BusinessAuditCommand command) {
        auditWriter.appendBusiness(new BusinessAuditEntry(
                Ids.newId(),
                command.treeKey(),
                command.actorUserExternalId(),
                command.entityType(),
                command.entityExternalId(),
                command.memberExternalId(),
                command.action(),
                command.fieldChanged(),
                redactor.redact(command.entityType(), command.previousData()),
                redactor.redact(command.entityType(), command.newData())));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSecurity(SecurityAuditCommand command) {
        String userAgent = command.userAgent();
        if (userAgent != null && userAgent.length() > MAX_USER_AGENT) {
            userAgent = userAgent.substring(0, MAX_USER_AGENT);
        }
        auditWriter.appendSecurity(new SecurityAuditEvent(
                command.eventType(),
                command.outcome(),
                command.userExternalId(),
                hashOrNull(normalizeEmail(command.email())),
                hashOrNull(command.ipAddress()),
                userAgent,
                redactor.sanitizePayload(command.detail())));
    }

    /** Legacy auth-service normalization: trim + lower-case before hashing. */
    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static byte[] hashOrNull(String value) {
        return value == null || value.isBlank() ? null : Hashes.sha256(value);
    }
}
