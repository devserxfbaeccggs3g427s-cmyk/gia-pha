package com.familya.member.adapter.out.events;

import com.familya.member.application.port.out.MemberEventPublisher;
import com.familya.member.domain.event.MemberEvent;
import com.familya.platform.outbox.JdbcOutboxWriter;
import com.familya.platform.outbox.OutboxWriter;
import com.familya.platform.telemetry.PlatformMetrics;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Triển khai {@link MemberEventPublisher} sử dụng outbox. Sự kiện thành viên được stage lên
 * outbox cục bộ và platform publisher sẽ phát chúng lên topic {@code member.events.v1}.
 *
 * <p>Bean {@code @Component} thuộc tầng adapter-out trong kiến trúc Hexagonal, đảm bảo tính
 * nguyên tử giữa thay đổi CSDL và phát sự kiện thông qua outbox.
 */
@Component
public class OutboxMemberEventPublisher implements MemberEventPublisher {

    private final OutboxWriter outbox;
    private final PlatformMetrics metrics;

    /**
     * Khởi tạo publisher với outbox writer và bộ thu thập metric.
     *
     * @param outbox  writer outbox nền tảng
     * @param metrics bộ metric dùng để đếm sự kiện đã stage
     */
    public OutboxMemberEventPublisher(OutboxWriter outbox, PlatformMetrics metrics) {
        this.outbox = outbox;
        this.metrics = metrics;
    }

    /**
     * Stage sự kiện thành viên lên outbox với các header cần thiết.
     *
     * @param event sự kiện cần phát (MemberCreated, MemberUpdated, MemberTombstoned, v.v.)
     */
    @Override
    public void publish(MemberEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("treeId", event.treeId().toString());
        payload.put("memberId", event.memberId().toString());
        payload.put("eventType", event.eventType());
        payload.put("eventVersion", event.eventVersion());
        payload.put("occurredAt", event.occurredAt().toString());
        JdbcOutboxWriter.Builder b = JdbcOutboxWriter.builder()
                .create("member", event.memberId().toString(), 1L,
                        event.eventType(), event.eventVersion(),
                        event.topic(), event.partitionKey(), payload);
        b.header("eventType", event.eventType());
        b.header("eventVersion", String.valueOf(event.eventVersion()));
        b.header("treeId", event.treeId().toString());
        b.header("memberId", event.memberId().toString());
        outbox.stage(b.build());
        metrics.outboxStaged("member-service", event.eventType());
    }
}