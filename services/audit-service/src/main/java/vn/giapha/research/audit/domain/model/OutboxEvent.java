package vn.giapha.research.audit.domain.model;

import java.time.Instant;

/**
 * Read model of one {@code outbox_events} row. The payload is carried as the
 * raw allowlisted JSON document; handlers parse what they need. Delivery is
 * at-least-once — handlers must be idempotent (design.md §Audit and
 * Idempotency).
 */
public record OutboxEvent(
        long outboxEventKey,
        String aggregateType,
        String aggregateExternalId,
        Long treeKey,
        String eventType,
        String payloadJson,
        OutboxStatus status,
        int attempts,
        Instant availableAt,
        Instant leasedUntil,
        String leasedBy,
        String lastError,
        Instant completedAt,
        Instant createdAt) {
}
