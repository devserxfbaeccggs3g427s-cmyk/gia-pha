package com.familya.treeaccess.adapter.out.events;

import com.familya.treeaccess.application.port.out.TreeEventPublisher;
import com.familya.treeaccess.domain.event.MembershipEvent;
import com.familya.treeaccess.domain.event.TreeEvent;
import com.familya.platform.outbox.JdbcOutboxWriter;
import com.familya.platform.outbox.OutboxWriter;
import com.familya.platform.telemetry.PlatformMetrics;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Outbox adapter for tree and membership events. Both topics are
 * partitioned by treeId (Kafka {@code partition_key} header), so the
 * ordered stream is preserved per tree.
 */
@Component
public class OutboxTreeEventPublisher implements TreeEventPublisher {

    /** Bộ ghi outbox dùng để stage sự kiện cây/thành viên. */
    private final OutboxWriter outbox;

    /** Bộ thu thập số liệu — ghi nhận mỗi sự kiện được stage. */
    private final PlatformMetrics metrics;

    /**
     * Khởi tạo publisher với outbox writer và metric.
     *
     * @param outbox  bộ ghi outbox
     * @param metrics metric giám sát
     */
    public OutboxTreeEventPublisher(OutboxWriter outbox, PlatformMetrics metrics) {
        this.outbox = outbox;
        this.metrics = metrics;
    }

    /**
     * Stage một sự kiện cây lên outbox. Partition key là {@code treeId} để
     * giữ thứ tự trong cùng một cây; header bổ sung giúp consumer truy vết nhanh.
     *
     * @param event sự kiện cây cần publish
     */
    @Override
    public void publishTreeEvent(TreeEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("treeId", event.treeId().toString());
        payload.put("revision", event.revision());
        payload.put("epoch", event.epoch());
        payload.put("eventType", event.eventType());
        payload.put("eventVersion", event.eventVersion());
        payload.put("occurredAt", event.occurredAt().toString());
        JdbcOutboxWriter.Builder b = JdbcOutboxWriter.builder()
                .create("tree", event.treeId().toString(), event.revision(),
                        event.eventType(), event.eventVersion(),
                        event.topic(), event.partitionKey(), payload);
        b.header("eventType", event.eventType());
        b.header("eventVersion", String.valueOf(event.eventVersion()));
        b.header("revision", String.valueOf(event.revision()));
        b.header("epoch", String.valueOf(event.epoch()));
        outbox.stage(b.build());
        metrics.outboxStaged("tree-access-service", event.eventType());
    }

    /**
     * Stage một sự kiện thành viên (cấp/thu hồi quyền) lên outbox.
     * Khoá của message gồm {@code treeId}:{@code userId} để bảo toàn
     * thứ tự cho mọi thay đổi của cùng một thành viên.
     *
     * @param event sự kiện thành viên cần publish
     */
    @Override
    public void publishMembershipEvent(MembershipEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("treeId", event.treeId().toString());
        payload.put("userId", event.userId().toString());
        payload.put("eventType", event.eventType());
        payload.put("eventVersion", event.eventVersion());
        payload.put("occurredAt", event.occurredAt().toString());
        JdbcOutboxWriter.Builder b = JdbcOutboxWriter.builder()
                .create("membership", event.treeId() + ":" + event.userId(), 1L,
                        event.eventType(), event.eventVersion(),
                        event.topic(), event.partitionKey(), payload);
        b.header("eventType", event.eventType());
        b.header("eventVersion", String.valueOf(event.eventVersion()));
        b.header("treeId", event.treeId().toString());
        b.header("userId", event.userId().toString());
        outbox.stage(b.build());
        metrics.outboxStaged("tree-access-service", event.eventType());
    }
}