package com.familya.auditops.application.port.out;

import com.familya.auditops.domain.model.AuditEvent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Append-only audit port. Implementations MUST reject updates and
 * deletes. Reads are served for operator and security review only.
 */
public interface AuditAppender {

    AuditEvent append(AuditEvent event);

    List<AuditEvent> findByOperation(UUID operationId, int limit);

    Optional<AuditEvent> findById(UUID auditId);
}