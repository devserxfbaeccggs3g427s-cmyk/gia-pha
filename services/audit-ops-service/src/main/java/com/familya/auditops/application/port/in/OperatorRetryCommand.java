package com.familya.auditops.application.port.in;

import java.util.Objects;
import java.util.UUID;

/**
 * Operator action: retry a Saga that ended in
 * {@code MANUAL_REVIEW} or that previously failed compensation.
 * The orchestrator increments the attempt counter, re-issues the
 * un-acked step commands, and transitions the operation to
 * {@code RUNNING}. The action is recorded in the audit log.
 */
public final class OperatorRetryCommand {

    private final UUID operatorUserId;
    private final UUID operationId;
    private final String reason;

    public OperatorRetryCommand(UUID operatorUserId, UUID operationId, String reason) {
        this.operatorUserId = Objects.requireNonNull(operatorUserId);
        this.operationId = Objects.requireNonNull(operationId);
        this.reason = Objects.requireNonNull(reason);
    }

    public UUID operatorUserId() { return operatorUserId; }
    public UUID operationId() { return operationId; }
    public String reason() { return reason; }
}