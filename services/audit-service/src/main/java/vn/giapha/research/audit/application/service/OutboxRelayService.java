package vn.giapha.research.audit.application.service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import vn.giapha.research.audit.application.port.out.OutboxEventHandler;
import vn.giapha.research.audit.application.port.out.OutboxRepository;
import vn.giapha.research.audit.config.AuditOperationsSettings;
import vn.giapha.research.audit.domain.model.OutboxEvent;
import vn.giapha.research.audit.support.TimeProvider;

/**
 * Outbox relay (Task 12.4, Requirement 13.8): claims bounded batches under a
 * durable {@code FOR UPDATE SKIP LOCKED} lease, dispatches to the registered
 * per-event-type handler and applies exponential backoff with full jitter
 * until the attempt budget dead-letters the event.
 *
 * <p>Handlers run <em>outside</em> the claim transaction; a crash between
 * handler success and the completion update redelivers the event after lease
 * expiry — at-least-once with idempotent handlers, never exactly-once.
 */
@Service
public class OutboxRelayService {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayService.class);

    private static final int ERROR_MAX = 2000; // outbox_events.last_error

    private final OutboxRepository outboxRepository;
    private final Map<String, OutboxEventHandler> handlers;
    private final AuditOperationsSettings settings;
    private final TimeProvider time;
    private final OutboxMetrics metrics;

    public OutboxRelayService(OutboxRepository outboxRepository,
            List<OutboxEventHandler> handlerBeans, AuditOperationsSettings settings,
            TimeProvider time, OutboxMetrics metrics) {
        this.outboxRepository = outboxRepository;
        this.handlers = indexHandlers(handlerBeans);
        this.settings = settings;
        this.time = time;
        this.metrics = metrics;
    }

    /**
     * One relay cycle: reclaim expired leases, claim a bounded batch for this
     * worker, dispatch each event, refresh queue metrics. Returns the number
     * of events processed so pollers can spin while the queue is hot.
     */
    public int runOnce(String workerId) {
        Instant now = time.now();
        int reclaimed = outboxRepository.reclaimExpiredLeases(now, settings.outboxBatchSize());
        if (reclaimed > 0) {
            log.warn("Reclaimed {} expired outbox lease(s)", reclaimed);
        }
        List<OutboxEvent> batch = outboxRepository.claimBatch(
                workerId, now, settings.leaseDuration(), settings.outboxBatchSize());
        for (OutboxEvent event : batch) {
            dispatch(event);
        }
        metrics.update(outboxRepository.stats(time.now()));
        return batch.size();
    }

    private void dispatch(OutboxEvent event) {
        OutboxEventHandler handler = handlers.get(event.eventType());
        try {
            if (handler == null) {
                // Retriable: the owning module may not be deployed yet; the
                // event dead-letters once the budget runs out and is replayable.
                throw new IllegalStateException(
                        "No handler registered for event type " + event.eventType());
            }
            handler.handle(event);
            outboxRepository.markCompleted(event.outboxEventKey(), time.now());
            metrics.completed();
        } catch (RuntimeException e) {
            failed(event, e);
        }
    }

    private void failed(OutboxEvent event, RuntimeException e) {
        String error = truncate(e.toString());
        if (event.attempts() >= settings.maxAttempts()) {
            log.error("Outbox event {} ({}) dead-lettered after {} attempts",
                    event.outboxEventKey(), event.eventType(), event.attempts(), e);
            outboxRepository.markDead(event.outboxEventKey(), error);
            metrics.deadLettered();
            return;
        }
        Instant nextAvailableAt = time.now().plus(nextDelay(event.attempts()));
        log.warn("Outbox event {} ({}) failed attempt {}/{}; retrying at {}",
                event.outboxEventKey(), event.eventType(), event.attempts(),
                settings.maxAttempts(), nextAvailableAt, e);
        outboxRepository.markFailedForRetry(event.outboxEventKey(), error, nextAvailableAt);
        metrics.retried();
    }

    /** Exponential backoff capped at {@code backoffMax}, with full jitter and a 1s floor. */
    private Duration nextDelay(int attempts) {
        long baseMillis = settings.backoffBase().toMillis();
        long capMillis = settings.backoffMax().toMillis();
        int exponent = Math.min(attempts - 1, 30); // avoid overflow on long shifts
        long ceiling = Math.min(capMillis, baseMillis << exponent);
        long jittered = ThreadLocalRandom.current().nextLong(ceiling + 1);
        return Duration.ofMillis(Math.max(1000, jittered));
    }

    private static String truncate(String error) {
        return error.length() <= ERROR_MAX ? error : error.substring(0, ERROR_MAX);
    }

    private static Map<String, OutboxEventHandler> indexHandlers(List<OutboxEventHandler> beans) {
        Map<String, OutboxEventHandler> byType = new HashMap<>();
        for (OutboxEventHandler handler : beans) {
            OutboxEventHandler previous = byType.putIfAbsent(handler.eventType(), handler);
            if (previous != null) {
                throw new IllegalStateException("Duplicate outbox handlers for event type "
                        + handler.eventType() + ": " + previous.getClass().getName()
                        + " and " + handler.getClass().getName());
            }
        }
        return Map.copyOf(byType);
    }
}
