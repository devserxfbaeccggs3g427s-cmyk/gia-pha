/**
 * Cấu hình khai báo các Kafka topic cho dịch vụ audit-ops.
 * <p>
 * Hai topic chính được tạo tại đây:
 * </p>
 * <ul>
 *   <li>{@code saga.replies.v1}: nơi các participant service gửi phản hồi
 *       Saga trở lại cho orchestrator.</li>
 *   <li>Mỗi topic {@code saga.commands.<participant>.v1} được tạo tự động
 *       thông qua relay outbox dựa trên tên participant.</li>
 * </ul>
 *
 * <p>Việc khai báo topic tại đây giúp dịch vụ có thể khởi động trong một
 * môi trường Kafka sạch mà không cần thực hiện bước admin riêng biệt.</p>
 */
package com.familya.auditops.adapter.in.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Lớp cấu hình ({@link Configuration}) chứa các {@link Bean} khai báo
 * topic Kafka cho service. Số partition và replication factor được cấu
 * hình qua thuộc tính {@code familya.kafka.partitions} và
 * {@code familya.kafka.replication-factor} với giá trị mặc định hợp lý
 * cho môi trường production (12 partition, 3 replica).
 */
@Configuration
public class AuditOpsKafkaTopicConfig {

    /** Số partition mặc định cho các topic chính. */
    @Value("${familya.kafka.partitions:12}")
    private int partitions;

    /** Hệ số replication mặc định. */
    @Value("${familya.kafka.replication-factor:3}")
    private short replicationFactor;

    /**
     * Khai báo topic {@code auditops.events.v1} - nơi dịch vụ phát các
     * event vòng đời của operation. Topic này được giữ 180 ngày để phục
     * vụ truy vấn lịch sử và replay.
     *
     * @return bean {@link NewTopic} tương ứng
     */
    @Bean
    public NewTopic auditOpsEvents() {
        return TopicBuilder.name("auditops.events.v1")
                .partitions(partitions)
                .replicas(replicationFactor)
                .config("retention.ms", "15768000000")  // 180 ngày
                .config("min.insync.replicas", "2")
                .build();
    }

    /**
     * Khai báo topic {@code saga.replies.v1} - nơi participant service gửi
     * phản hồi Saga. Topic dùng chung cho mọi bounded context nên được giữ
     * 180 ngày và bảo đảm tối thiểu 2 replica đồng bộ.
     *
     * @return bean {@link NewTopic} tương ứng
     */
    @Bean
    public NewTopic sagaReplies() {
        return TopicBuilder.name("saga.replies.v1")
                .partitions(partitions)
                .replicas(replicationFactor)
                .config("retention.ms", "15768000000")  // 180 ngày
                .config("min.insync.replicas", "2")
                .build();
    }

    /**
     * Khai báo DLQ {@code auditops.events.v1.dlq} - nơi chứa các message
     * không xử lý được. Số partition nhỏ hơn (6) và retention ngắn hơn
     * (14 ngày) vì các message lỗi cần được xử lý và xoá sớm.
     *
     * @return bean {@link NewTopic} tương ứng
     */
    @Bean
    public NewTopic auditOpsEventsDlq() {
        return TopicBuilder.name("auditops.events.v1.dlq")
                .partitions(6)
                .replicas(replicationFactor)
                .config("retention.ms", "1209600000")   // 14 ngày
                .build();
    }
}