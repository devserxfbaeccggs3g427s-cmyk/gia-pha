package com.familya.auditops.adapter.out.events;

import com.familya.auditops.application.port.out.SagaCommandBus;
import com.familya.platform.outbox.JdbcOutboxWriter;
import com.familya.platform.outbox.OutboxWriter;
import com.familya.platform.telemetry.PlatformMetrics;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stages Saga commands in the local outbox. The relay publishes them
 * to the participant service's command topic; the participant in turn
 * ack / nack on the Saga reply topic. Topic name is derived from the
 * participant service name so a service may subscribe to its own
 * queue without needing an environment-specific routing table.
 */
@Component
public class OutboxSagaCommandPublisher implements SagaCommandBus {

    private final OutboxWriter outbox;
    private final PlatformMetrics metrics;

    public OutboxSagaCommandPublisher(OutboxWriter outbox, PlatformMetrics metrics) {
        this.outbox = outbox;
        this.metrics = metrics;
    }

    @Override
    public void dispatchCommand(SagaCommand cmd) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operationId", cmd.operationId().toString());
        payload.put("correlationId", cmd.correlationId() == null ? null : cmd.correlationId().toString());
        payload.put("participantService", cmd.participantService());
        payload.put("stepName", cmd.stepName());
        payload.put("sequenceNo", cmd.sequenceNo());
        payload.put("commandType", cmd.commandType());
        payload.put("payloadJson", cmd.payloadJson());
        payload.put("expectedVersion", cmd.expectedVersion());
        payload.put("targetRevision", cmd.targetRevision());
        payload.put("targetEpoch", cmd.targetEpoch());
        payload.put("deadline", cmd.deadline() == null ? null : cmd.deadline().toString());

        String topic = "saga.commands." + cmd.participantService() + ".v1";
        JdbcOutboxWriter.Builder b = JdbcOutboxWriter.builder()
                .create("saga-command", cmd.operationId().toString() + ":" + cmd.stepName(),
                        cmd.sequenceNo(), cmd.commandType(), 1, topic, cmd.operationId().toString(), payload);
        b.header("eventType", cmd.commandType());
        b.header("eventVersion", "1");
        b.header("operationId", cmd.operationId().toString());
        b.header("correlationId", cmd.correlationId() == null ? "" : cmd.correlationId().toString());
        b.header("participantService", cmd.participantService());
        b.header("stepName", cmd.stepName());
        if (cmd.headers() != null) {
            cmd.headers().forEach(b::header);
        }
        outbox.stage(b.build());
        metrics.outboxStaged("audit-ops-service", cmd.commandType());
    }
}