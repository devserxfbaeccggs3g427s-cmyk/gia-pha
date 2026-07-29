package com.familya.identity.adapter.in.kafka;

import com.familya.identity.application.port.out.IdentityRepository;
import com.familya.platform.inbox.InboxStore;
import com.familya.platform.telemetry.PlatformMetrics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class IdentityEventListener {

    private static final Logger LOG = LoggerFactory.getLogger(IdentityEventListener.class);

    private final IdentityRepository repo;
    private final InboxStore inbox;
    private final PlatformMetrics metrics;

    public IdentityEventListener(IdentityRepository repo, InboxStore inbox, PlatformMetrics metrics) {
        this.repo = repo;
        this.inbox = inbox;
        this.metrics = metrics;
    }

    @KafkaListener(topics = "tree.memberships.v1", groupId = "${spring.application.name}")
    public void onMembership(ConsumerRecord<String, Object> record) {
        if (!shouldProcess(record, "tree.memberships.v1")) return;
        LOG.info("Received membership event key={} offset={}", record.key(), record.offset());
        metrics.consumerProcessed("identity-service", "membership");
    }

    @KafkaListener(topics = "member.events.v1", groupId = "${spring.application.name}")
    public void onMember(ConsumerRecord<String, Object> record) {
        if (!shouldProcess(record, "member.events.v1")) return;
        LOG.info("Received member event key={} offset={}", record.key(), record.offset());
        metrics.consumerProcessed("identity-service", "member");
    }

    private boolean shouldProcess(ConsumerRecord<String, Object> record, String topic) {
        String eventId = headerString(record, "event_id");
        if (eventId == null) {
            LOG.warn("Dropping record without event_id topic={} offset={}", topic, record.offset());
            return false;
        }
        if (inbox.exists(eventId, "identity-service")) {
            metrics.consumerDuplicate("identity-service", topic);
            return false;
        }
        inbox.markProcessed(new com.familya.platform.inbox.InboxRecord(
                eventId, "identity-service", topic, record.partition(), record.offset(), Instant.now()));
        return true;
    }

    private static String headerString(ConsumerRecord<?, ?> record, String name) {
        var h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value());
    }
}
