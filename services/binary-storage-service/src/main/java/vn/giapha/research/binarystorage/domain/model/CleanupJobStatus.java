package vn.giapha.research.binarystorage.domain.model;

/**
 * Durable cleanup job lifecycle (Task 15.8, ck_file_cleanup_jobs_status).
 * Mirrors the outbox worker state machine: claims are leased, failures retry
 * with backoff, and exhausted or unrecoverable jobs park as {@code DEAD} for
 * operator review instead of silently disappearing.
 */
public enum CleanupJobStatus {
    /** Enqueued; eligible once {@code available_at} passes. */
    PENDING,
    /** Claimed under a lease by one worker. */
    IN_PROGRESS,
    /** Object confirmed gone (deleted, or already absent) — terminal. */
    COMPLETED,
    /** Last attempt failed; will be retried after backoff. */
    FAILED,
    /** Retries exhausted or precondition permanently violated — needs an operator. */
    DEAD
}
