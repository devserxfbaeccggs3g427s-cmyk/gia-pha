package vn.giapha.research.binarystorage.application.service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import vn.giapha.research.binarystorage.application.port.out.BinaryObjectStore;
import vn.giapha.research.binarystorage.application.port.out.FileCleanupJobRepository;
import vn.giapha.research.binarystorage.config.UploadLifecycleSettings;
import vn.giapha.research.binarystorage.domain.error.BinaryStorageException;
import vn.giapha.research.binarystorage.domain.model.CleanupJob;
import vn.giapha.research.binarystorage.shared.time.TimeProvider;

/**
 * Durable, idempotent binary deletion (Task 15.8, Req 13.10). Jobs become
 * eligible after {@code available_at}; deletion goes through the
 * {@link BinaryObjectStore} whose delete treats an absent object as success,
 * so replays and redeliveries converge instead of failing.
 *
 * <p>Failure handling mirrors the outbox relay: transient store failures back
 * off exponentially with full jitter, exhausted jobs park as {@code DEAD},
 * and an ETag precondition mismatch parks immediately — the object changed
 * after the job was enqueued, so deleting it blindly would destroy data.
 */
@Service
public class CleanupService {

    private static final Logger log = LoggerFactory.getLogger(CleanupService.class);

    private final FileCleanupJobRepository jobs;
    private final BinaryObjectStore objectStore;
    private final UploadLifecycleSettings settings;
    private final TimeProvider time;

    CleanupService(FileCleanupJobRepository jobs, BinaryObjectStore objectStore,
            UploadLifecycleSettings settings, TimeProvider time) {
        this.jobs = jobs;
        this.objectStore = objectStore;
        this.settings = settings;
        this.time = time;
    }

    /** One poll cycle: reclaim stuck leases, claim a batch, execute it. */
    public int runOnce(String workerId) {
        Instant now = time.now();
        int reclaimed = jobs.reclaimExpiredLeases(now, settings.cleanupBatchSize());
        if (reclaimed > 0) {
            log.warn("Reclaimed {} expired cleanup leases", reclaimed);
        }
        var batch = jobs.claimBatch(workerId, now, settings.cleanupLease(), settings.cleanupBatchSize());
        for (CleanupJob job : batch) {
            process(job);
        }
        return batch.size();
    }

    private void process(CleanupJob job) {
        try {
            objectStore.delete(job.objectPath(), job.expectedEtag());
            jobs.markCompleted(job.id(), time.now());
        } catch (BinaryStorageException e) {
            if (e.reason() == BinaryStorageException.Reason.PRECONDITION_FAILED) {
                // The object is not the one we were asked to delete anymore.
                // Never delete blindly — park for an operator (Task 14.3 ETag DoD).
                jobs.markDead(job.id(), "ETag precondition failed; object changed since enqueue");
                log.warn("Cleanup job {} ({}) parked DEAD: ETag mismatch", job.id(), job.reason());
                return;
            }
            retryOrPark(job, e.reason() + ": " + e.getMessage());
        } catch (RuntimeException e) {
            retryOrPark(job, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void retryOrPark(CleanupJob job, String error) {
        // attempts was already incremented by the claim.
        if (job.attempts() >= settings.cleanupMaxAttempts()) {
            jobs.markDead(job.id(), error);
            log.error("Cleanup job {} ({}) exhausted {} attempts: {}",
                    job.id(), job.reason(), job.attempts(), error);
            return;
        }
        Instant nextAt = time.now().plus(backoff(job.attempts()));
        jobs.markFailedForRetry(job.id(), error, nextAt);
        log.warn("Cleanup job {} attempt {} failed; retrying: {}", job.id(), job.attempts(), error);
    }

    /** Exponential backoff with full jitter: random(0, min(cap, base * 2^(attempts-1))). */
    private Duration backoff(int attempts) {
        long baseMillis = settings.cleanupBackoffBase().toMillis();
        long capMillis = settings.cleanupBackoffMax().toMillis();
        int shift = Math.min(attempts - 1, 30);
        long delay = Math.min(capMillis, baseMillis << shift);
        return Duration.ofMillis(ThreadLocalRandom.current().nextLong(delay + 1));
    }
}
