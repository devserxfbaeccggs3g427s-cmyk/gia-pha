package vn.giapha.research.audit.application.service;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import vn.giapha.research.audit.application.port.out.AuditWriter;
import vn.giapha.research.audit.application.port.out.IdempotencyRepository;
import vn.giapha.research.audit.application.port.out.OutboxRepository;
import vn.giapha.research.audit.config.AuditOperationsSettings;
import vn.giapha.research.audit.support.TimeProvider;

/**
 * Bounded retention sweeps for the operational tables this module owns
 * (Requirement 13.3 — business and security audit have separate retention).
 *
 * <p>Every sweep is idempotent and row-capped ({@code retentionSweepBatch}),
 * so a long backlog is drained across cycles without long-running deletes.
 * A retention of {@link Duration#ZERO} disables the corresponding sweep;
 * business audit defaults to disabled because the legacy backend kept its
 * {@code ChangeLog} history forever.
 */
@Service
public class RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

    private final AuditWriter auditWriter;
    private final IdempotencyRepository idempotencyRepository;
    private final OutboxRepository outboxRepository;
    private final AuditOperationsSettings settings;
    private final TimeProvider time;

    public RetentionService(AuditWriter auditWriter, IdempotencyRepository idempotencyRepository,
            OutboxRepository outboxRepository, AuditOperationsSettings settings,
            TimeProvider time) {
        this.auditWriter = auditWriter;
        this.idempotencyRepository = idempotencyRepository;
        this.outboxRepository = outboxRepository;
        this.settings = settings;
        this.time = time;
    }

    /** One retention cycle; returns the total number of rows purged. */
    public int runOnce() {
        Instant now = time.now();
        int limit = settings.retentionSweepBatch();
        int purged = 0;
        purged += idempotencyRepository.deleteExpired(now, limit);
        purged += outboxRepository.deleteCompletedBefore(
                now.minus(settings.completedOutboxRetention()), limit);
        if (!settings.businessAuditRetention().isZero()) {
            purged += auditWriter.purgeBusinessBefore(
                    now.minus(settings.businessAuditRetention()), limit);
        }
        if (!settings.securityAuditRetention().isZero()) {
            purged += auditWriter.purgeSecurityBefore(
                    now.minus(settings.securityAuditRetention()), limit);
        }
        if (purged > 0) {
            log.info("Retention sweep purged {} row(s)", purged);
        }
        return purged;
    }
}
