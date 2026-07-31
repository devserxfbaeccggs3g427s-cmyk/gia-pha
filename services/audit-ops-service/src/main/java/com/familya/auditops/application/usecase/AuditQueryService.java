package com.familya.auditops.application.usecase;

import com.familya.auditops.application.port.out.AuditAppender;
import com.familya.auditops.domain.model.AuditEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Read-side service that returns audit events. Authorisation is
 * enforced at the controller boundary; this service only answers
 * read queries.
 */
@Service
public class AuditQueryService {

    private final AuditAppender audit;

    public AuditQueryService(AuditAppender audit) {
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> byOperation(UUID operationId, int limit) {
        return audit.findByOperation(operationId, Math.min(Math.max(limit, 1), 1000));
    }

    @Transactional(readOnly = true)
    public AuditEvent byId(UUID auditId) {
        return audit.findById(auditId).orElseThrow(() ->
                new com.familya.platform.error.NotFoundException("Audit " + auditId + " not found"));
    }
}