package com.familya.auditops.application.port.out;

/**
 * Ports for downstream publish targets that the orchestrator must
 * reach when a Saga step transitions. The interface is intentionally
 * minimal: the orchestrator sends a Saga command envelope and
 * publishes through the local outbox. Each bounded context
 * implements its own gRPC / Kafka producer in its own service; this
 * service only stages the command.
 */
public interface SagaCommandBus {

    /**
     * Stage a Saga command for a participant service. The envelope
     * includes the operation id, the step name, the target
     * revision/epoch, and the deadline. The relay publishes to the
     * participant's command topic.
     */
    void dispatchCommand(SagaCommand command);

    record SagaCommand(
            java.util.UUID operationId,
            java.util.UUID correlationId,
            String participantService,
            String stepName,
            int sequenceNo,
            String commandType,
            String payloadJson,
            Long expectedVersion,
            Long targetRevision,
            Long targetEpoch,
            java.time.Instant deadline,
            java.util.Map<String, String> headers
    ) { }
}