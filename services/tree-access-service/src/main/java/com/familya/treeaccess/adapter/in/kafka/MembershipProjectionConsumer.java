package com.familya.treeaccess.adapter.in.kafka;

import com.familya.platform.inbox.InboxStore;
import com.familya.platform.telemetry.PlatformMetrics;
import com.familya.treeaccess.application.port.out.TreeRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Membership consumer — re-builds the projection row from the
 * authoritative event stream. The local projection in Tree Access is
 * written in the same transaction as the event publication, so this
 * consumer is the replay/reconciliation path (used by Kafka replay,
 * projection rebuild, and cutover).
 */
@Component
public class MembershipProjectionConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(MembershipProjectionConsumer.class);

    private final TreeRepository repo;
    private final InboxStore inbox;
    private final PlatformMetrics metrics;

    public MembershipProjectionConsumer(TreeRepository repo, InboxStore inbox, PlatformMetrics metrics) {
        this.repo = repo;
        this.inbox = inbox;
        this.metrics = metrics;
    }

    @KafkaListener(topics = "tree.memberships.v1", groupId = "${spring.application.name}")
    public void onMembership(ConsumerRecord<String, Object> record) {
        if (!shouldProcess(record, "tree.memberships.v1")) return;
        // The local publisher writes the projection in the same
        // transaction as the outbox, so this consumer is only used for
        // replay/cutover. The actual projection rebuild is performed
        // by the reconciliation endpoint.
        metrics.consumerProcessed("tree-access-service", "membership");
        LOG.info("Processed membership event offset={} key={}", record.offset(), record.key());
    }

    @KafkaListener(topics = "tree.events.v1", groupId = "${spring.application.name}")
    public void onTree(ConsumerRecord<String, Object> record) {
        if (!shouldProcess(record, "tree.events.v1")) return;
        metrics.consumerProcessed("tree-access-service", "tree");
        LOG.info("Processed tree event offset={} key={}", record.offset(), record.key());
    }

    private boolean shouldProcess(ConsumerRecord<String, Object> record, String topic) {
        String eventId = headerString(record, "event_id");
        if (eventId == null) {
            LOG.warn("Dropping record without event_id topic={} offset={}", topic, record.offset());
            return false;
        }
        if (inbox.exists(eventId, "tree-access-service")) {
            metrics.consumerDuplicate("tree-access-service", topic);
            return false;
        }
        inbox.markProcessed(new com.familya.platform.inbox.InboxRecord(
                eventId, "tree-access-service", topic, record.partition(), record.offset(), Instant.now()));
        return true;
    }

    private static String headerString(ConsumerRecord<?, ?> record, String name) {
        var h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value());
    }
}