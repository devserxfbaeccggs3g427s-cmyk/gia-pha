package vn.giapha.research.audit.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Audit-consumer scaffolding. audit-service consumes audit-relevant
 * events from every other service and persists them in its own
 * schema. The actual persistence happens in
 * {@code audit.application.service.AuditService}; this class is the
 * inbound adapter.
 *
 * <p>For Phase 6.2 (reconciliation) this consumer also emits a
 * security audit if the event is a delete.
 */
@Component
public class AuditEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditEventConsumer.class);

    private final ObjectMapper mapper;

    public AuditEventConsumer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @RabbitListener(queues = "audit-service.audit-events")
    public void onAuditEvent(Map<String, Object> envelope) {
        String type = (String) envelope.get("eventType");
        log.info("audit-service consumed: {} aggregate={}", type, envelope.get("aggregateId"));
        // TODO: forward to AuditService.record(...). The actual
        // recordAuditUseCase.record(...) call lives in audit's domain.
    }
}
