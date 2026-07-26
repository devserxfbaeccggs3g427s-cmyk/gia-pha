package vn.giapha.research.audit.application.port.in;

import vn.giapha.research.audit.domain.model.OutboxEvent;
import vn.giapha.research.audit.domain.model.OutboxStatus;
import vn.giapha.research.audit.support.PageRequest;
import vn.giapha.research.audit.support.PageResult;

/**
 * Operator-facing outbox management (Requirement 13.9): inspect, replay and
 * cancel jobs without touching SQL. Exposed over the OPS-guarded web adapter.
 */
public interface OperateOutboxUseCase {

    PageResult<OutboxEvent> list(OutboxStatus status, PageRequest page);

    OutboxEvent get(long outboxEventKey);

    /**
     * Requeues a COMPLETED, FAILED or DEAD event with a fresh attempt budget.
     * Replaying a COMPLETED event is legal because handlers are idempotent.
     */
    OutboxEvent replay(long outboxEventKey);

    /**
     * Dead-letters a PENDING or FAILED event so no worker will run it.
     * Events under a live lease cannot be cancelled mid-flight.
     */
    OutboxEvent cancel(long outboxEventKey);
}
