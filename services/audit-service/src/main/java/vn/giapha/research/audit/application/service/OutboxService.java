package vn.giapha.research.audit.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import vn.giapha.research.audit.application.port.in.EnqueueOutboxUseCase;
import vn.giapha.research.audit.application.port.out.OutboxRepository;
import vn.giapha.research.audit.domain.model.NewOutboxEvent;

/**
 * Transactional outbox append (Task 12.3, Requirement 13.7). MANDATORY
 * propagation makes the atomicity contract structural: an event can only be
 * enqueued from inside the business transaction whose effects it describes,
 * so mutation, revision, audit and outbox commit or vanish together.
 */
@Service
public class OutboxService implements EnqueueOutboxUseCase {

    private final OutboxRepository outboxRepository;
    private final AuditRedactor redactor;

    public OutboxService(OutboxRepository outboxRepository, AuditRedactor redactor) {
        this.outboxRepository = outboxRepository;
        this.redactor = redactor;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public long enqueue(NewOutboxEvent event) {
        return outboxRepository.append(new NewOutboxEvent(
                event.aggregateType(),
                event.aggregateExternalId(),
                event.treeKey(),
                event.eventType(),
                redactor.sanitizePayload(event.payload())));
    }
}
