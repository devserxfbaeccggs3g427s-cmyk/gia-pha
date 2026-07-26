package vn.giapha.research.audit.application.service;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.giapha.research.audit.application.port.in.OperateOutboxUseCase;
import vn.giapha.research.audit.application.port.out.OutboxRepository;
import vn.giapha.research.audit.domain.model.OutboxEvent;
import vn.giapha.research.audit.domain.model.OutboxStatus;
import vn.giapha.research.audit.support.ConflictException;
import vn.giapha.research.audit.support.NotFoundException;
import vn.giapha.research.audit.support.PageRequest;
import vn.giapha.research.audit.support.PageResult;
import vn.giapha.research.audit.support.TimeProvider;

/**
 * Operator outbox management (Task 12.4, Requirement 13.9): every inspection,
 * replay and cancellation goes through this service — never through SQL.
 * State guards mirror the queue's lease semantics so operators cannot race a
 * live worker.
 */
@Service
public class OutboxOperationsService implements OperateOutboxUseCase {

    private final OutboxRepository outboxRepository;
    private final TimeProvider time;

    public OutboxOperationsService(OutboxRepository outboxRepository, TimeProvider time) {
        this.outboxRepository = outboxRepository;
        this.time = time;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<OutboxEvent> list(OutboxStatus status, PageRequest page) {
        return outboxRepository.list(status, page);
    }

    @Override
    @Transactional(readOnly = true)
    public OutboxEvent get(long outboxEventKey) {
        return require(outboxEventKey);
    }

    @Override
    @Transactional
    public OutboxEvent replay(long outboxEventKey) {
        OutboxEvent event = require(outboxEventKey);
        if (event.status() == OutboxStatus.PENDING || event.status() == OutboxStatus.IN_PROGRESS) {
            throw new ConflictException(
                    "Outbox event is already queued or running and cannot be replayed",
                    Map.of("status", event.status().name()));
        }
        outboxRepository.resetForReplay(outboxEventKey, time.now());
        return require(outboxEventKey);
    }

    @Override
    @Transactional
    public OutboxEvent cancel(long outboxEventKey) {
        OutboxEvent event = require(outboxEventKey);
        if (event.status() != OutboxStatus.PENDING && event.status() != OutboxStatus.FAILED) {
            throw new ConflictException(
                    "Only pending or failed outbox events can be cancelled",
                    Map.of("status", event.status().name()));
        }
        outboxRepository.markDead(outboxEventKey, "Cancelled by operator");
        return require(outboxEventKey);
    }

    private OutboxEvent require(long outboxEventKey) {
        return outboxRepository.find(outboxEventKey)
                .orElseThrow(() -> new NotFoundException("Outbox event not found"));
    }
}
