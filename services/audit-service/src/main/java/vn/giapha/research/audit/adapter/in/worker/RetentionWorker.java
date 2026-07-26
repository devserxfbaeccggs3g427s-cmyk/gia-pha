package vn.giapha.research.audit.adapter.in.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import vn.giapha.research.audit.application.service.RetentionService;

/**
 * Scheduled retention sweeper. Sweeps are idempotent and row-capped, so
 * overlapping instances or a long backlog are safe — each cycle just purges
 * up to the batch cap and leaves the remainder for the next one.
 */
@Component
class RetentionWorker {

    private static final Logger log = LoggerFactory.getLogger(RetentionWorker.class);

    private final RetentionService retentionService;

    RetentionWorker(RetentionService retentionService) {
        this.retentionService = retentionService;
    }

    @Scheduled(
            initialDelayString = "PT1M",
            fixedDelayString = "${giapha.workers.retention-poll-interval:PT1H}")
    void sweep() {
        try {
            retentionService.runOnce();
        } catch (RuntimeException e) {
            log.error("Retention sweep failed; will retry on next cycle", e);
        }
    }
}
