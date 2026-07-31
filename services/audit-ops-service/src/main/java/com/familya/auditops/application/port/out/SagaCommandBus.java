/**
 * Port cho các đích publish xuôi chiều mà orchestrator phải gọi khi
 * một Saga step chuyển trạng thái.
 *
 * <p>Interface được giữ cố ý tối giản: orchestrator gửi một envelope
 * Saga command và publish thông qua outbox cục bộ. Mỗi bounded context
 * triển khai gRPC/Kafka producer của riêng mình; dịch vụ này chỉ
 * stage command.</p>
 */
package com.familya.auditops.application.port.out;

/**
 * Interface publish Saga command cho participant service thông qua outbox.
 *
 * <p>Triển khai mặc định là {@code OutboxSagaCommandPublisher}.</p>
 */
public interface SagaCommandBus {

    /**
     * Stage một Saga command cho participant service. Envelope gồm:
     * operation id, step name, target revision/epoch và deadline.
     * Relay sẽ publish lên topic lệnh của participant.
     *
     * @param command Saga command cần dispatch
     */
    void dispatchCommand(SagaCommand command);

    /**
     * Saga command envelope gửi tới participant service.
     *
     * @param operationId        UUID operation
     * @param correlationId      correlation id
     * @param participantService tên participant
     * @param stepName           tên step
     * @param sequenceNo         thứ tự step
     * @param commandType        loại command
     * @param payloadJson        payload JSON
     * @param expectedVersion    version mong đợi
     * @param targetRevision     revision mục tiêu
     * @param targetEpoch        epoch mục tiêu
     * @param deadline           thời hạn xử lý
     * @param headers            header bổ sung
     */
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