package vn.giapha.research.binarystorage.adapter.in.worker;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import vn.giapha.research.binarystorage.application.service.CleanupService;

/**
 * Scheduled cleanup poller (Task 15.8). Same shape as the outbox worker: a
 * stable {@code hostname#suffix} id is recorded on every lease, the queue is
 * drained back-to-back while claims come back non-empty, and any number of
 * instances can run concurrently thanks to {@code FOR UPDATE SKIP LOCKED}.
 */
@Component
class FileCleanupWorker {

    private static final Logger log = LoggerFactory.getLogger(FileCleanupWorker.class);

    private final CleanupService cleanupService;
    private final String workerId;

    FileCleanupWorker(CleanupService cleanupService) {
        this.cleanupService = cleanupService;
        this.workerId = hostName() + "#"
                + HexFormat.of().toHexDigits(ThreadLocalRandom.current().nextInt(), 8);
        log.info("File cleanup worker id: {}", workerId);
    }

    @Scheduled(fixedDelayString = "${giapha.workers.cleanup-poll-interval:PT1M}")
    void poll() {
        try {
            while (cleanupService.runOnce(workerId) > 0) {
                // keep claiming
            }
        } catch (RuntimeException e) {
            // Per-job failures are handled inside the service; reaching here
            // means the claim/reclaim itself failed (e.g. DB outage). Log and
            // let the next tick retry rather than killing the scheduled task.
            log.error("Cleanup cycle failed; will retry on next poll", e);
        }
    }

    private static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown-host";
        }
    }
}
