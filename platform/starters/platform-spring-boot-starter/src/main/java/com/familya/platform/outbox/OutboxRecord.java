package com.familya.platform.outbox;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Transactional outbox record. Producers commit domain state and
 * outbox rows in a single local transaction; a separate relay
 * (see {@link OutboxRelay}) reads the outbox and publishes to Kafka
 * with the required metadata headers.
 */
public record OutboxRecord(
        UUID id,
        String aggregateType,
        String aggregateId,
        long aggregateVersion,
        String eventType,
        int eventVersion,
        String topic,
        String partitionKey,
        String correlationId,
        String causationId,
        String operationId,
        String traceparent,
        String payloadJson,
        Map<String, String> headers,
        Instant occurredAt,
        Instant lockedUntil
) {
    public OutboxRecord {
        if (headers == null) {
            headers = Map.of();
        }
    }
}
