package vn.giapha.research.audit.adapter.in.worker;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import vn.giapha.research.audit.application.service.OutboxRelayService;

/**
 * Scheduled outbox poller (Task 12.4, Requirement 13.8). Each instance owns a
 * stable worker id ({@code hostname#suffix}) recorded on the lease so stuck
 * leases are attributable in {@code outbox_events.leased_by}.
 *
 * <p>The poll interval is a placeholder with the same default as
 * {@code GiaPhaProperties.Workers#outboxPollInterval}; when the queue is hot
 * the worker drains full batches back-to-back before sleeping, so the
 * interval only governs idle latency. Any number of instances can run
 * concurrently — {@code FOR UPDATE SKIP LOCKED} keeps their claims disjoint.
 */
@Component
class OutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);

    private final OutboxRelayService relayService;
    private final String workerId;

    OutboxWorker(OutboxRelayService relayService) {
        this.relayService = relayService;
        this.workerId = hostName() + "#"
                + HexFormat.of().toHexDigits(ThreadLocalRandom.current().nextInt(), 8);
        log.info("Outbox worker id: {}", workerId);
    }

    @Scheduled(fixedDelayString = "${giapha.workers.outbox-poll-interval:PT2S}")
    void poll() {
        try {
            // Drain while claims come back non-empty; sleep only when idle.
            while (relayService.runOnce(workerId) > 0) {
                // keep claiming
            }
        } catch (RuntimeException e) {
            // Per-event failures are handled inside the relay; reaching here
            // means the claim/reclaim itself failed (e.g. DB outage). Log and
            // let the next tick retry rather than killing the scheduled task.
            log.error("Outbox relay cycle failed; will retry on next poll", e);
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
