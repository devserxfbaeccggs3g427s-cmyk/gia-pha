package com.familya.platform.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic declarations. Services add the topics they publish to
 * or subscribe from. Replication factor and partition count are
 * baseline values that may be overridden per environment.
 */
@Configuration
public class KafkaTopicConfig {

    @Value("${familya.kafka.partitions:12}")
    private int partitions;

    @Value("${familya.kafka.replication-factor:3}")
    private short replicationFactor;

    @Bean
    public NewTopic identityEvents() {
        return TopicBuilder.name("identity.events.v1")
                .partitions(partitions)
                .replicas(replicationFactor)
                .config("retention.ms", "220752000000") // 7y
                .config("min.insync.replicas", "2")
                .build();
    }

    @Bean
    public NewTopic identityEventsDlq() {
        return TopicBuilder.name("identity.events.v1.dlq")
                .partitions(6)
                .replicas(replicationFactor)
                .config("retention.ms", "1209600000")  // 14d
                .build();
    }
}
