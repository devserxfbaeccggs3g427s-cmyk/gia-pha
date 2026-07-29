package com.familya.platform.projection;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Replay & reconciliation ledger. Services append an entry for every
 * watermark transition they observe from a source topic. The ledger
 * answers two questions:
 *
 * <ol>
 *   <li>What is the highest source revision this consumer has
 *       acknowledged? (drives the freshness check)</li>
 *   <li>Are there gaps? If an entry's {@code lastSeenOffset} is not
 *       strictly +1 from the previous entry's {@code lastSeenOffset}
 *       for the same partition, the consumer must trigger replay.</li>
 * </ol>
 *
 * <p>This is intentionally framework-independent. Each consumer
 * persists the ledger row in its local MySQL; the reconciliation
 * pipeline reads the ledger across services to compute the global
 * cutover gate.</p>
 */
public final class ReplayLedger {

    private ReplayLedger() { }

    public static Entry newEntry(UUID aggregateId, String topic, int partition,
                                  long offset, long aggregateRevision, long epoch) {
        return new Entry(aggregateId, topic, partition, offset, aggregateRevision, epoch,
                java.time.Instant.now());
    }

    public static Map<String, Object> toMap(Entry e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("aggregateId", e.aggregateId().toString());
        m.put("topic", e.topic());
        m.put("partition", e.partition());
        m.put("lastSeenOffset", e.lastSeenOffset());
        m.put("aggregateRevision", e.aggregateRevision());
        m.put("epoch", e.epoch());
        m.put("recordedAt", e.recordedAt().toString());
        return m;
    }

    public record Entry(
            UUID aggregateId,
            String topic,
            int partition,
            long lastSeenOffset,
            long aggregateRevision,
            long epoch,
            java.time.Instant recordedAt
    ) { }
}