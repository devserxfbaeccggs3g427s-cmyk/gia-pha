package vn.giapha.research.audit.domain.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import vn.giapha.research.audit.support.ValidationException;

/**
 * Append command for the transactional outbox. The payload must already have
 * passed {@code AuditRedactor#sanitizePayload}: only allowlisted, secret-free
 * values may leave the business transaction.
 */
public record NewOutboxEvent(
        String aggregateType,
        String aggregateExternalId,
        Long treeKey,
        String eventType,
        Map<String, Object> payload) {

    public NewOutboxEvent {
        if (aggregateType == null || aggregateType.isBlank()) {
            throw new ValidationException("Outbox event requires an aggregate type");
        }
        if (aggregateExternalId == null || aggregateExternalId.isBlank()) {
            throw new ValidationException("Outbox event requires an aggregate id");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new ValidationException("Outbox event requires an event type");
        }
        // Not Map.copyOf: sanitized payloads may carry null values.
        payload = payload == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }
}
