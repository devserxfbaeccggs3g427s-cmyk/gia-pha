package com.familya.auditops.adapter.in.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Topic declarations for the audit-ops service. Saga reply and
 * per-participant Saga command topics are created here so the
 * service can boot in a clean environment without a separate Kafka
 * admin step.
 */
@Configuration
public class AuditOpsKafkaTopicConfig {

    @Value("${familya.kafka.partitions:12}")
    private int partitions;

    @Value("${familya.kafka.replication-factor:3}")
    private short replicationFactor;

    @Bean
    public NewTopic auditOpsEvents() {
        return TopicBuilder.name("auditops.events.v1")
                .partitions(partitions)
                .replicas(replicationFactor)
                .config("retention.ms", "15768000000")  // 180d
                .config("min.insync.replicas", "2")
                .build();
    }

    @Bean
    public NewTopic sagaReplies() {
        return TopicBuilder.name("saga.replies.v1")
                .partitions(partitions)
                .replicas(replicationFactor)
                .config("retention.ms", "15768000000")  // 180d
                .config("min.insync.replicas", "2")
                .build();
    }

    @Bean
    public NewTopic auditOpsEventsDlq() {
        return TopicBuilder.name("auditops.events.v1.dlq")
                .partitions(6)
                .replicas(replicationFactor)
                .config("retention.ms", "1209600000")   // 14d
                .build();
    }
}