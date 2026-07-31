/**
 * Stage Saga command vào outbox cục bộ.
 *
 * <p>Relay sẽ publish các command này lên topic lệnh của participant
 * tương ứng; participant ack/nack thông qua topic {@code saga.replies.v1}.
 * Tên topic được dẫn xuất từ tên participant service nên mỗi dịch vụ có
 * thể subscribe queue riêng mà không cần bảng routing theo môi trường.</p>
 */
package com.familya.auditops.adapter.out.events;

import com.familya.auditops.application.port.out.SagaCommandBus;
import com.familya.platform.outbox.JdbcOutboxWriter;
import com.familya.platform.outbox.OutboxWriter;
import com.familya.platform.telemetry.PlatformMetrics;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Triển khai {@link SagaCommandBus} sử dụng outbox.
 *
 * <p>Mỗi command được stage thành một outbox row với aggregate type là
 * {@code "saga-command"}; key là {@code <operationId>:<stepName>} để
 * idempotent.</p>
 */
@Component
public class OutboxSagaCommandPublisher implements SagaCommandBus {

    /** Outbox writer của platform. */
    private final OutboxWriter outbox;
    /** Metric collector. */
    private final PlatformMetrics metrics;

    /**
     * Khởi tạo publisher.
     *
     * @param outbox  outbox writer
     * @param metrics metric collector
     */
    public OutboxSagaCommandPublisher(OutboxWriter outbox, PlatformMetrics metrics) {
        this.outbox = outbox;
        this.metrics = metrics;
    }

    /**
     * Stage một Saga command cho participant.
     *
     * <p>Các bước:</p>
     * <ol>
     *   <li>Chuyển command thành {@code Map} payload.</li>
     *   <li>Tính tên topic theo quy ước {@code saga.commands.<participant>.v1}.</li>
     *   <li>Dựng builder với aggregate type {@code "saga-command"} và key
     *       {@code <operationId>:<stepName>}.</li>
     *   <li>Thêm các header chuẩn (eventType, eventVersion, operationId,
     *       correlationId, participantService, stepName).</li>
     *   <li>Thêm các header do command cung cấp.</li>
     *   <li>Stage row và ghi metric.</li>
     * </ol>
     *
     * @param cmd Saga command cần dispatch
     */
    @Override
    public void dispatchCommand(SagaCommand cmd) {
        // Payload cho outbox row.
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

        // Topic riêng cho từng participant để chúng tự subscribe.
        String topic = "saga.commands." + cmd.participantService() + ".v1";
        JdbcOutboxWriter.Builder b = JdbcOutboxWriter.builder()
                .create("saga-command", cmd.operationId().toString() + ":" + cmd.stepName(),
                        cmd.sequenceNo(), cmd.commandType(), 1, topic, cmd.operationId().toString(), payload);
        // Header chuẩn theo convention của platform.
        b.header("eventType", cmd.commandType());
        b.header("eventVersion", "1");
        b.header("operationId", cmd.operationId().toString());
        b.header("correlationId", cmd.correlationId() == null ? "" : cmd.correlationId().toString());
        b.header("participantService", cmd.participantService());
        b.header("stepName", cmd.stepName());
        // Header bổ sung từ command (ví dụ: traceparent).
        if (cmd.headers() != null) {
            cmd.headers().forEach(b::header);
        }
        outbox.stage(b.build());
        metrics.outboxStaged("audit-ops-service", cmd.commandType());
    }
}