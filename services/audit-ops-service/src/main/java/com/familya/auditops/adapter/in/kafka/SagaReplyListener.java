/**
 * Listener tiêu thụ các phản hồi Saga từ mọi participant service
 * trên topic chia sẻ {@code saga.replies.v1}.
 *
 * <p>Audit Ops KHÔNG phải orchestrator Saga (ADR-003). Listener này
 * chỉ chiếu (project) reply cho operator UI; nó không transition
 * state machine nào và không publish Saga command. Vì vậy mọi reply
 * lỗi được ghi vào {@code saga_reply_dead_letter} thay vì retry vô
 * hạn: replay thuộc trách nhiệm của owning service, không phải Audit Ops.</p>
 */
package com.familya.auditops.adapter.in.kafka;

import com.familya.auditops.adapter.out.persistence.SagaReplyDeadLetterStore;
import com.familya.platform.inbox.InboxRecord;
import com.familya.platform.inbox.InboxStore;
import com.familya.platform.telemetry.PlatformMetrics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class SagaReplyListener {

    private static final Logger LOG = LoggerFactory.getLogger(SagaReplyListener.class);
    private static final String CONSUMER = "audit-ops-service.saga-replies";

    private final InboxStore inbox;
    private final PlatformMetrics metrics;
    private final SagaReplyDeadLetterStore deadLetterStore;

    public SagaReplyListener(InboxStore inbox,
                             PlatformMetrics metrics,
                             SagaReplyDeadLetterStore deadLetterStore) {
        this.inbox = inbox;
        this.metrics = metrics;
        this.deadLetterStore = deadLetterStore;
    }

    @KafkaListener(topics = "saga.replies.v1", groupId = "${spring.application.name:audit-ops-service}.saga-replies")
    @Transactional
    public void onReply(ConsumerRecord<String, Object> record) {
        String eventId = headerString(record, "event_id");
        if (eventId == null) {
            deadLetterStore.savePoison(record, new IllegalArgumentException("Missing event_id header"));
            return;
        }
        if (inbox.exists(eventId, CONSUMER)) {
            metrics.consumerDuplicate(CONSUMER, record.topic());
            return;
        }
        inbox.markProcessed(new InboxRecord(
                eventId, CONSUMER, record.topic(), record.partition(), record.offset(), Instant.now()));

        Object value = record.value();
        if (value == null) {
            deadLetterStore.savePoison(record, new IllegalArgumentException("Null reply payload"));
            return;
        }
        String s = value instanceof String str ? str : String.valueOf(value);
        if (s.isBlank()) {
            deadLetterStore.savePoison(record, new IllegalArgumentException("Blank reply payload"));
            return;
        }
        try {
            var n = new com.fasterxml.jackson.databind.ObjectMapper().readTree(s);
            if (!n.hasNonNull("operationId") || !n.hasNonNull("participantService") || !n.hasNonNull("stepName") || !n.hasNonNull("outcome")) {
                deadLetterStore.savePoison(record, new IllegalArgumentException("Missing required reply fields"));
                return;
            }
        } catch (Exception parseError) {
            deadLetterStore.savePoison(record, parseError);
            return;
        }
        metrics.consumerProcessed(CONSUMER, record.topic());
    }

    private static String headerString(ConsumerRecord<?, ?> record, String name) {
        var h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value());
    }
}
