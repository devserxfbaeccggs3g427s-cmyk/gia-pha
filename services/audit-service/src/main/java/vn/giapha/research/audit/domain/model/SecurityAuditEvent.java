package vn.giapha.research.audit.domain.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import vn.giapha.research.audit.support.ValidationException;

/**
 * One security event destined for {@code security_audit_logs}.
 *
 * <p>Kept under a separate schema/retention from business history
 * (Requirement 13.3). Email and client IP are stored only as SHA-256 hashes
 * so rows can outlive account deletion without holding direct PII; there is
 * deliberately no FK to {@code users}.
 */
public record SecurityAuditEvent(
        String eventType,
        Outcome outcome,
        String userExternalId,
        byte[] emailHash,
        byte[] ipHash,
        String userAgent,
        Map<String, Object> detail) {

    /** Mirrors {@code ck_security_audit_logs_outcome}. */
    public enum Outcome {
        SUCCESS,
        FAILURE,
        BLOCKED
    }

    public SecurityAuditEvent {
        if (eventType == null || eventType.isBlank()) {
            throw new ValidationException("Security event requires an event type");
        }
        if (outcome == null) {
            throw new ValidationException("Security event requires an outcome");
        }
        emailHash = emailHash == null ? null : emailHash.clone();
        ipHash = ipHash == null ? null : ipHash.clone();
        // Not Map.copyOf: detail maps may carry null values.
        detail = detail == null ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(detail));
    }
}
