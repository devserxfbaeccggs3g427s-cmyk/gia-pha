package com.familya.platform.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListenerConfigurer;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistrar;
import org.springframework.kafka.config.MethodKafkaListenerEndpoint;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.clients.producer.ProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.util.backoff.FixedBackOff;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Kafka consumer factory, error handler, and DLQ recoverer. The
 * default error handler retries 3 times with a 1s backoff and then
 * routes the failed record to {@code <topic>.dlq}. Consumers SHOULD
 * declare an idempotency key (typically {@code event_id}) to make
 * replays safe.
 */
@Configuration
public class KafkaConsumerConfig implements KafkaListenerConfigurer {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Value("${spring.application.name:unknown}")
    private String serviceName;

    @Autowired
    private KafkaProperties properties;

    @Autowired(required = false)
    private ProducerFactory<Object, Object> producerFactory;

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> cfg = new HashMap<>(properties.buildConsumerProperties());
        cfg.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        cfg.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        cfg.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS,
                org.apache.kafka.common.serialization.StringDeserializer.class);
        cfg.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        cfg.put(JsonDeserializer.TRUSTED_PACKAGES, "com.familya.*,com.familya.platform.*");
        cfg.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        cfg.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "java.util.Map");
        return new DefaultKafkaConsumerFactory<>(cfg);
    }

    @Bean
    public KafkaListenerContainerFactory<?> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(3);
        factory.getContainerProperties().setObservationEnabled(true);
        if (producerFactory != null) {
            DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                    new KafkaTemplate<>(producerFactory),
                    (record, ex) -> new TopicPartition(record.topic() + ".dlq", record.partition()));
            DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
            factory.setCommonErrorHandler(errorHandler);
        }
        return factory;
    }

    @Override
    public void configureKafkaListeners(KafkaListenerEndpointRegistrar registrar) {
        // The Discoverer hooks are configured by @KafkaListener annotations on consumer methods.
    }

    /**
     * Default idempotent listener wrapper. Consumers should call this
     * from their {@code @KafkaListener} method to guarantee
     * deduplication.
     */
    public static <T> boolean shouldProcess(ConsumerRecord<String, T> record, String consumerName,
                                            java.util.function.Function<String, Boolean> exists) {
        String eventId = headerString(record, "event_id");
        if (eventId == null) {
            LOG.warn("Dropping record without event_id topic={} partition={} offset={}",
                    record.topic(), record.partition(), record.offset());
            return false;
        }
        return !exists.apply(eventId);
    }

    private static String headerString(ConsumerRecord<?, ?> record, String name) {
        var h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value());
    }
}
