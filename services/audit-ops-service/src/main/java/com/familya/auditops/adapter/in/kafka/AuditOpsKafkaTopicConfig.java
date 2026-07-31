/**
 * Cấu hình khai báo các Kafka topic cho dịch vụ audit-ops.
 *
 * <p>Audit Ops chỉ tiêu thụ hai topic (projection + reply); theo ADR-003
 * nó không phát Saga command nên không khai báo topic command.</p>
 *
 * <ul>
 *   <li>{@code operations.events.v1}: owner service phát OperationStarted/
 *       OperationStateChanged; Audit Ops project thành operation_lifecycle_projection.</li>
 *   <li>{@code saga.replies.v1}: participant service phát SagaParticipantReply;
 *       Audit Ops chỉ ghi nhận malformed message vào DLQ, không transition
 *       state machine nào.</li>
 * </ul>
 */
package com.familya.auditops.adapter.in.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class AuditOpsKafkaTopicConfig {

    @Value("${familya.kafka.partitions:12}")
    private int partitions;

    @Value("${familya.kafka.replication-factor:3}")
    private short replicationFactor;

    @Bean
    public NewTopic sagaReplies() {
        return TopicBuilder.name("saga.replies.v1")
                .partitions(partitions)
                .replicas(replicationFactor)
                .config("retention.ms", "15768000000")
                .config("min.insync.replicas", "2")
                .build();
    }
}
