package vn.giapha.research.binarystorage.domain.model;

import java.time.Instant;

/**
 * Persisted upload intent (Task 15.2): the exact quarantine and final paths,
 * the constraints baked into the browser capability, and the expiry after
 * which reconciliation reaps the quarantine object. Paths are
 * server-generated and never influenced by client input (Req 7.4).
 */
public record UploadIntent(
        long id,
        String externalId,
        long treeKey,
        Long mediaKey,
        Long requestedByUserKey,
        String quarantineObjectPath,
        String finalObjectPath,
        String expectedMimeType,
        long expectedMaxBytes,
        byte[] expectedSha256,
        UploadIntentStatus status,
        Instant expiresAt,
        Instant completedAt,
        long version,
        Instant createdAt) {
}
