package vn.giapha.research.audit.application.port.in;

import vn.giapha.research.audit.domain.model.NewOutboxEvent;

/**
 * Published inbound port for appending outbox work (Requirement 13.7).
 * Must be called inside the same business transaction as the mutation it
 * describes; the append uses propagation MANDATORY so a standalone call
 * fails fast instead of silently breaking atomicity.
 */
public interface EnqueueOutboxUseCase {

    /** Returns the internal key of the appended event. */
    long enqueue(NewOutboxEvent event);
}
