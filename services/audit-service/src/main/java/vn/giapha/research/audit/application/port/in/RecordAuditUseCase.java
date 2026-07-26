package vn.giapha.research.audit.application.port.in;

import java.util.Map;

import vn.giapha.research.audit.domain.model.AuditAction;
import vn.giapha.research.audit.domain.model.AuditEntityType;
import vn.giapha.research.audit.domain.model.SecurityAuditEvent;

/**
 * Published inbound port for audit recording (Requirement 13.1-13.4).
 * Other modules call this — never the tables — per the cross-module rule in
 * design.md §Rules.
 */
public interface RecordAuditUseCase {

    /**
     * Records one business change <em>inside the caller's transaction</em>
     * (propagation MANDATORY): a rolled-back mutation leaves no audit row.
     * Payload maps are redacted against the per-entity allowlist before they
     * are persisted — callers pass the plain legacy-shaped entity maps.
     */
    void recordBusiness(BusinessAuditCommand command);

    /**
     * Records one security event in its own transaction (REQUIRES_NEW): the
     * row survives even when the surrounding business work rolls back, e.g.
     * a blocked login attempt. Raw email/IP are hashed here and never stored.
     */
    void recordSecurity(SecurityAuditCommand command);

    record BusinessAuditCommand(
            long treeKey,
            String actorUserExternalId,
            AuditEntityType entityType,
            String entityExternalId,
            String memberExternalId,
            AuditAction action,
            String fieldChanged,
            Map<String, Object> previousData,
            Map<String, Object> newData) {
    }

    record SecurityAuditCommand(
            String eventType,
            SecurityAuditEvent.Outcome outcome,
            String userExternalId,
            String email,
            String ipAddress,
            String userAgent,
            Map<String, Object> detail) {
    }
}
