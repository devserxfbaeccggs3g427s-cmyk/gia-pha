package vn.giapha.research.binarystorage.adapter.in.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import vn.giapha.research.binarystorage.application.service.UploadReconciliationService;

/**
 * Scheduled reconciliation pass (Task 15.7): expiry, stuck-intent re-drive,
 * orphan sweep and the overdue-cleanup alert. Every phase is idempotent and
 * transition-guarded, so overlapping runs across instances are harmless.
 */
@Component
class UploadReconciliationWorker {

    private static final Logger log = LoggerFactory.getLogger(UploadReconciliationWorker.class);

    private final UploadReconciliationService reconciliationService;

    UploadReconciliationWorker(UploadReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @Scheduled(fixedDelayString = "${giapha.workers.upload-reconcile-interval:PT5M}")
    void reconcile() {
        try {
            reconciliationService.runOnce();
        } catch (RuntimeException e) {
            // A failing dependency (store listing, DB) aborts this pass only;
            // reconciliation is a convergence loop, the next tick catches up.
            log.error("Upload reconciliation pass failed; will retry on next tick", e);
        }
    }
}
