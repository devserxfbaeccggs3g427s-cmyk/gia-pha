package com.familya.auditops.application.port.in;

import java.util.Objects;
import java.util.UUID;

/**
 * Operator action: cancel a non-terminal operation. Issuing this
 * command transitions the operation to {@code MANUAL_REVIEW} so the
 * cancel is visible in the audit log; subsequent operator actions
 * decide the actual cleanup.
 */
public final class CancelOperationCommand {

    private final UUID operatorUserId;
    private final UUID operationId;
    private final String reason;

    public CancelOperationCommand(UUID operatorUserId, UUID operationId, String reason) {
        this.operatorUserId = Objects.requireNonNull(operatorUserId);
        this.operationId = Objects.requireNonNull(operationId);
        this.reason = Objects.requireNonNull(reason);
    }

    public UUID operatorUserId() { return operatorUserId; }
    public UUID operationId() { return operationId; }
    public String reason() { return reason; }
}