package vn.giapha.research.binarystorage.application.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import vn.giapha.research.binarystorage.application.port.in.CompleteUploadUseCase;
import vn.giapha.research.binarystorage.application.port.out.BinaryObjectStore;
import vn.giapha.research.binarystorage.application.port.out.FileCleanupJobRepository;
import vn.giapha.research.binarystorage.application.port.out.UploadIntentRepository;
import vn.giapha.research.binarystorage.config.UploadLifecycleSettings;
import vn.giapha.research.binarystorage.domain.model.CleanupReasons;
import vn.giapha.research.binarystorage.domain.model.ObjectPage;
import vn.giapha.research.binarystorage.domain.model.StoredObjectSummary;
import vn.giapha.research.binarystorage.domain.model.UploadIntent;
import vn.giapha.research.binarystorage.domain.model.UploadIntentStatus;
import vn.giapha.research.binarystorage.shared.time.TimeProvider;

/**
 * Upload reconciliation (Task 15.7): the safety net that makes the lifecycle
 * converge no matter where a browser, callback, worker or this process died.
 *
 * <ul>
 *   <li><b>Expiry:</b> {@code PENDING_UPLOAD} intents past {@code expires_at}
 *       become {@code EXPIRED} and their quarantine object is enqueued for
 *       deletion (abandoned uploads).</li>
 *   <li><b>Re-drive:</b> {@code UPLOADED} intents past {@code expires_at} are
 *       pushed through the completion pipeline again — this is how uploads
 *       stranded by a scanner outage or a crash eventually promote.</li>
 *   <li><b>Orphan sweep:</b> quarantine objects older than the minimum age
 *       with no live intent (spoofed callbacks, lost rows, multipart debris)
 *       are enqueued for deletion.</li>
 *   <li><b>Overdue alert:</b> warns only when unfinished cleanup work is older
 *       than {@code available_at + 24h} (DoD).</li>
 * </ul>
 */
@Service
public class UploadReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(UploadReconciliationService.class);

    /** Pages of quarantine listing examined per sweep; keeps a pass bounded. */
    private static final int MAX_SWEEP_PAGES = 10;
    private static final int SWEEP_PAGE_SIZE = 100;
    private static final String QUARANTINE_PREFIX = "quarantine/";

    private final UploadIntentRepository intents;
    private final FileCleanupJobRepository cleanupJobs;
    private final BinaryObjectStore objectStore;
    private final CompleteUploadUseCase completeUpload;
    private final TransactionTemplate transaction;
    private final UploadLifecycleSettings settings;
    private final TimeProvider time;

    UploadReconciliationService(
            UploadIntentRepository intents,
            FileCleanupJobRepository cleanupJobs,
            BinaryObjectStore objectStore,
            CompleteUploadUseCase completeUpload,
            TransactionTemplate transaction,
            UploadLifecycleSettings settings,
            TimeProvider time) {
        this.intents = intents;
        this.cleanupJobs = cleanupJobs;
        this.objectStore = objectStore;
        this.completeUpload = completeUpload;
        this.transaction = transaction;
        this.settings = settings;
        this.time = time;
    }

    /** One reconciliation pass; each phase is independent and crash-safe. */
    public void runOnce() {
        Instant now = time.now();
        expireAbandoned(now);
        redriveStuck(now);
        sweepOrphans(now);
        alertOverdueCleanup(now);
    }

    private void expireAbandoned(Instant now) {
        var due = intents.findExpiredInStatus(UploadIntentStatus.PENDING_UPLOAD, now,
                settings.reconcileBatchSize());
        for (UploadIntent intent : due) {
            // Guarded transition + enqueue in one transaction: a completion
            // callback racing this sweep either wins the guard (and promotes)
            // or observes EXPIRED and stands down.
            transaction.executeWithoutResult(status -> {
                if (intents.transition(intent.id(), UploadIntentStatus.PENDING_UPLOAD,
                        UploadIntentStatus.EXPIRED, now)) {
                    cleanupJobs.enqueue(intent.quarantineObjectPath(), null,
                            CleanupReasons.EXPIRED_QUARANTINE, now);
                }
            });
        }
        if (!due.isEmpty()) {
            log.info("Expired {} abandoned upload intents", due.size());
        }
    }

    private void redriveStuck(Instant now) {
        var stuck = intents.findExpiredInStatus(UploadIntentStatus.UPLOADED, now,
                settings.reconcileBatchSize());
        for (UploadIntent intent : stuck) {
            try {
                completeUpload.handleCompletion(intent.externalId());
            } catch (RuntimeException e) {
                // Still failing (e.g. scanner outage persists) — stays UPLOADED
                // and is retried on the next pass. Fail closed, never activate.
                log.warn("Re-drive of stuck intent {} failed; will retry: {}",
                        intent.id(), e.getMessage());
            }
        }
    }

    private void sweepOrphans(Instant now) {
        Instant reapBefore = now.minus(settings.orphanMinAge());
        String cursor = null;
        int enqueued = 0;
        for (int page = 0; page < MAX_SWEEP_PAGES; page++) {
            ObjectPage listing = objectStore.list(QUARANTINE_PREFIX, SWEEP_PAGE_SIZE, cursor);
            for (StoredObjectSummary object : listing.objects()) {
                if (object.uploadedAt() != null && object.uploadedAt().isBefore(reapBefore)
                        && !intents.quarantinePathInUse(object.pathname())
                        && !cleanupJobs.hasUnfinishedJob(object.pathname())) {
                    cleanupJobs.enqueue(object.pathname(), null, CleanupReasons.ORPHAN_OBJECT, now);
                    enqueued++;
                }
            }
            if (!listing.hasMore()) {
                break;
            }
            cursor = listing.cursor();
        }
        if (enqueued > 0) {
            log.warn("Orphan sweep enqueued {} quarantine objects for deletion", enqueued);
        }
    }

    private void alertOverdueCleanup(Instant now) {
        long overdue = cleanupJobs.countOverdue(now.minus(settings.overdueAlertAge()));
        if (overdue > 0) {
            // The operations alert (DoD): fires only past available_at + 24h.
            log.error("ALERT {} cleanup jobs overdue beyond available_at + {}",
                    overdue, settings.overdueAlertAge());
        }
    }
}
