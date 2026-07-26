package vn.giapha.research.audit.domain.model;

import java.time.Instant;

/**
 * One idempotency record in {@code processed_commands} (design.md §Audit and
 * Idempotency). The scope encodes actor/tenant + operation; the request hash
 * fingerprints the payload so a reused key with a different request is
 * detectable; the cached response is replayed for retries of the same request.
 */
public record ProcessedCommand(
        long processedCommandKey,
        String scope,
        String idempotencyKey,
        byte[] requestHash,
        Integer responseStatus,
        String responseBody,
        Instant completedAt,
        Instant expiresAt,
        Instant createdAt) {

    public boolean completed() {
        return completedAt != null;
    }

    public boolean expiredAt(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
