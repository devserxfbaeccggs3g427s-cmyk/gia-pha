package vn.giapha.research.audit.domain.model;

/** Mirrors {@code ck_outbox_events_status}. */
public enum OutboxStatus {
    /** Ready to be claimed once {@code available_at} has passed. */
    PENDING,
    /** Claimed under a live lease; invisible to other workers. */
    IN_PROGRESS,
    /** Handler finished successfully (terminal, retained briefly for inspection). */
    COMPLETED,
    /** Handler failed; retriable once the backoff delay in {@code available_at} passes. */
    FAILED,
    /** Dead-lettered: attempts exhausted or cancelled by an operator; replay only. */
    DEAD
}
