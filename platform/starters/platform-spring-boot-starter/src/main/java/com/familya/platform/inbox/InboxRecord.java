package com.familya.platform.inbox;

import java.time.Instant;

/**
 * Inbox row. Consumers dedupe by {@code event_id}. Rows that exceed
 * {@code retention_days} are deleted by a scheduled job. The inbox is
 * the at-least-once safe boundary; downstream handlers are idempotent
 * by aggregate version.
 */
public record InboxRecord(
        String eventId,
        String consumer,
        String topic,
        int partition,
        long offset,
        Instant consumedAt
) { }
