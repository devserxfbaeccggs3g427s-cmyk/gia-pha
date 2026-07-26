package vn.giapha.research.audit.config;

import java.time.Duration;

/**
 * Tuning for the audit-operations module, provided as a bean by
 * {@code app-bootstrap} from the typed {@code giapha.workers} properties.
 * The module deliberately owns no {@code @ConfigurationProperties} class so
 * configuration binding stays centralized in the bootstrap (Task 7.1).
 */
public record AuditOperationsSettings(
        /** Outbox events claimed per worker cycle. */
        int outboxBatchSize,
        /** Lease duration; expired leases are reclaimed by any worker. */
        Duration leaseDuration,
        /** Attempts before an event dead-letters. */
        int maxAttempts,
        /** First retry delay; doubles per attempt (full jitter applied). */
        Duration backoffBase,
        /** Upper bound of the retry delay. */
        Duration backoffMax,
        /** Business audit retention; {@link Duration#ZERO} keeps rows forever (legacy parity). */
        Duration businessAuditRetention,
        /** Security audit retention (separate policy, Requirement 13.3). */
        Duration securityAuditRetention,
        /** How long COMPLETED outbox rows stay inspectable before purge. */
        Duration completedOutboxRetention,
        /** Row cap per retention sweep statement. */
        int retentionSweepBatch) {

    public AuditOperationsSettings {
        if (outboxBatchSize < 1 || maxAttempts < 1 || retentionSweepBatch < 1) {
            throw new IllegalArgumentException("Worker sizes/attempts must be >= 1");
        }
        if (leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("Lease duration must be positive");
        }
    }
}
