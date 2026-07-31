package com.familya.member.adapter.out.events;

import com.familya.platform.outbox.JdbcOutboxWriter;
import com.familya.platform.outbox.OutboxWriter;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Stages OperationStarted / OperationStateChanged lifecycle events on the
 * local outbox. Owned by every Saga owner service (Member, Tree Access, ...)
 * and consumed by Audit Ops as a projection. See catalog.yaml.
 */
/**
 * Stager cho các sự kiện vòng đời operation (OperationStarted / OperationStateChanged) lên
 * outbox cục bộ. Mỗi dịch vụ sở hữu Saga (Member, Tree Access, ...) dùng stager này; sự kiện
 * được Audit Ops tiêu thụ như một projection. Tham khảo {@code catalog.yaml}.
 *
 * <p>Bean {@code @Component} thuộc tầng adapter-out trong kiến trúc Hexagonal.
 */
@Component
public class OperationLifecycleOutboxStager {

    private final OutboxWriter outbox;

    /**
     * Khởi tạo stager với {@link OutboxWriter}.
     *
     * @param outbox writer outbox nền tảng
     */
    public OperationLifecycleOutboxStager(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    /**
     * Stage một sự kiện vòng đời operation lên outbox. Bản ghi outbox sẽ được platform publisher
     * phát lên topic tương ứng để Audit Ops xử lý.
     *
     * @param payload   dữ liệu sự kiện (phải chứa {@code operationId} và {@code ownerService})
     * @param topic     tên topic Kafka đích
     * @param eventType loại sự kiện (ví dụ: {@code OperationStarted}, {@code OperationStateChanged})
     */
    public void stage(Map<String, Object> payload, String topic, String eventType) {
        String operationId = String.valueOf(payload.get("operationId"));
        JdbcOutboxWriter.Builder b = JdbcOutboxWriter.builder()
                .create("operation", operationId, 1L,
                        eventType, 1, topic, operationId, payload);
        b.header("eventType", eventType);
        b.header("operationId", operationId);
        b.header("ownerService", String.valueOf(payload.getOrDefault("ownerService", "member-service")));
        outbox.stage(b.build());
    }
}