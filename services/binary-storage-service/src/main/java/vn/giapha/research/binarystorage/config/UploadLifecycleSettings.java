package vn.giapha.research.binarystorage.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tuning for the upload-intent and cleanup lifecycle (Task 15), provided as a
 * bean by {@code app-bootstrap} — the module owns no
 * {@code @ConfigurationProperties} class (Task 7.1).
 */
@ConfigurationProperties("giapha.upload-lifecycle")
public record UploadLifecycleSettings(
        /** How long a freshly issued intent stays valid before reconciliation reaps it. */
        Duration intentTtl,
        /** Minimum quarantine-object age before the orphan sweep may reap it. */
        Duration orphanMinAge,
        /** Row cap per reconciliation pass (expiry, re-drive). */
        int reconcileBatchSize,
        /** Jobs claimed per cleanup poll. */
        int cleanupBatchSize,
        /** Cleanup claim lease; crashed workers free their jobs after this. */
        Duration cleanupLease,
        /** Attempts before a cleanup job parks as DEAD. */
        int cleanupMaxAttempts,
        /** First cleanup retry delay; doubles per attempt with full jitter. */
        Duration cleanupBackoffBase,
        /** Cleanup retry delay ceiling. */
        Duration cleanupBackoffMax,
        /** Overdue alert threshold past available_at (DoD: 24h). */
        Duration overdueAlertAge) {

    public UploadLifecycleSettings {
        if (intentTtl == null || intentTtl.isNegative() || intentTtl.isZero()) {
            throw new IllegalArgumentException("intentTtl must be positive");
        }
        if (orphanMinAge == null || orphanMinAge.isNegative()) {
            throw new IllegalArgumentException("orphanMinAge must not be negative");
        }
        if (reconcileBatchSize < 1 || cleanupBatchSize < 1 || cleanupMaxAttempts < 1) {
            throw new IllegalArgumentException("batch sizes and max attempts must be >= 1");
        }
    }
}
