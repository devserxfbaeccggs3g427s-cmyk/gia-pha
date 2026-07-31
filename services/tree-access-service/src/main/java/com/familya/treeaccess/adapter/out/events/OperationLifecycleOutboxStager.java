package com.familya.treeaccess.adapter.out.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.familya.platform.outbox.JdbcOutboxWriter;
import com.familya.platform.outbox.OutboxWriter;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Bộ stage các sự kiện vòng đời thao tác Saga lên outbox.
 *
 * <p>Chỉ có một phương thức {@link #stage} linh hoạt, cho phép truyền topic
 * và eventType tuỳ ý. Header {@code ownerService} mặc định là
 * {@code "tree-access-service"} nếu payload không chỉ định.</p>
 */
@Component
public class OperationLifecycleOutboxStager {

    /** Bộ ghi outbox để truyền message tới Kafka. */
    private final OutboxWriter outbox;

    /**
     * Khởi tạo bộ stage.
     *
     * @param outbox bộ ghi outbox dùng chung
     */
    public OperationLifecycleOutboxStager(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    /**
     * Stage một sự kiện vòng đời thao tác lên outbox. Sự kiện sẽ được khoá theo
     * {@code operationId} để bảo toàn thứ tự cho mọi cập nhật liên quan đến cùng
     * một thao tác Saga.
     *
     * @param payload   nội dung sự kiện — phải chứa {@code operationId}
     * @param topic     topic Kafka đích
     * @param eventType tên loại sự kiện (ví dụ {@code OperationStarted}, {@code OperationStateChanged})
     */
    public void stage(Map<String, Object> payload, String topic, String eventType) {
        String operationId = String.valueOf(payload.get("operationId"));
        JdbcOutboxWriter.Builder b = JdbcOutboxWriter.builder()
                .create("operation", operationId, 1L,
                        eventType, 1, topic, operationId, payload);
        b.header("eventType", eventType);
        b.header("operationId", operationId);
        b.header("ownerService", String.valueOf(payload.getOrDefault("ownerService", "tree-access-service")));
        outbox.stage(b.build());
    }
}