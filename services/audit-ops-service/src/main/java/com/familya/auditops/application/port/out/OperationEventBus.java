package com.familya.auditops.application.port.out;

import com.familya.auditops.domain.event.AuditOpsEvent;

import java.util.Map;

/**
 * Port that publishes {@link AuditOpsEvent} to the local outbox in
 * the current transaction. Implementations are MANDATORY-bound so a
 * missing transaction surfaces a configuration error at startup.
 */
public interface OperationEventBus {
    void publish(AuditOpsEvent event, Map<String, String> headers);
}