package vn.giapha.research.binarystorage.domain.model;

import java.time.Instant;

/**
 * One durable deletion job for a blob object ({@code file_cleanup_jobs},
 * design.md §Deletion and Cleanup). Binary deletion is never performed inline
 * with a user request: rows are enqueued transactionally with the state
 * change that made the object garbage, then executed after
 * {@code available_at} by {@code CleanupService} — idempotently, because the
 * store treats deleting an absent object as success.
 */
public record CleanupJob(
        long id,
        String objectPath,
        /** Strong ETag guard; null deletes unconditionally. */
        String expectedEtag,
        /** Why the object became garbage; see {@link CleanupReasons}. */
        String reason,
        CleanupJobStatus status,
        int attempts,
        Instant availableAt,
        Instant leasedUntil,
        String leasedBy,
        String lastError,
        Instant completedAt,
        Instant createdAt) {
}
