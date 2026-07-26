package vn.giapha.research.audit.domain.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import vn.giapha.research.audit.support.ValidationException;

/**
 * One allowlisted business-history row destined for {@code audit_logs}
 * (legacy {@code ChangeLog}). Payload maps must already be redacted by
 * {@code AuditRedactor} before an entry is constructed; the append port
 * never redacts again.
 *
 * <p>Entity references use external ids so the row survives entity deletion
 * (design.md §Security and Operational Tables).
 */
public record BusinessAuditEntry(
        String externalId,
        long treeKey,
        String actorUserExternalId,
        AuditEntityType entityType,
        String entityExternalId,
        String memberExternalId,
        AuditAction action,
        String fieldChanged,
        Map<String, Object> previousData,
        Map<String, Object> newData) {

    public BusinessAuditEntry {
        if (externalId == null || externalId.isBlank()) {
            throw new ValidationException("Audit entry requires an external id");
        }
        if (actorUserExternalId == null || actorUserExternalId.isBlank()) {
            throw new ValidationException("Audit entry requires an actor");
        }
        if (entityType == null || action == null) {
            throw new ValidationException("Audit entry requires entity type and action");
        }
        if (entityExternalId == null || entityExternalId.isBlank()) {
            throw new ValidationException("Audit entry requires an entity id");
        }
        // Not Map.copyOf: legacy-shaped payloads legitimately carry null values.
        previousData = previousData == null ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(previousData));
        newData = newData == null ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(newData));
    }
}
