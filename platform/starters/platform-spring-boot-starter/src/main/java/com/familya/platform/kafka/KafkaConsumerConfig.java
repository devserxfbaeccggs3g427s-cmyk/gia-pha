package com.familya.platform.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.kafka.annotation.KafkaListenerConfigurer;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistrar;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Cấu hình consumer factory, error handler và DLQ recoverer cho Kafka.
 *
 * <p>Error handler mặc định thực hiện:</p>
 * <ol>
 *   <li>Retry tối đa 3 lần với backoff cố định 1 giây.</li>
 *   <li>Nếu vẫn thất bại, chuyển bản ghi sang {@code <topic>.dlq} để xử lý
 *       thủ công hoặc phân tích sau.</li>
 * </ol>
 *
 * <p>Consumer NÊN khai báo idempotency key (thường là {@code event_id}) để
 * đảm bảo replay an toàn — vì cơ chế at-least-once của Kafka có thể gửi
 * trùng message khi có rebalance hoặc retry.</p>
 *
 * <p><b>Lưu ý tương thích:</b> Spring Boot 4 đã loại bỏ class
 * {@code KafkaProperties} autoconfig; do đó lớp này đọc trực tiếp các thuộc
 * tính {@code spring.kafka.bootstrap-servers} và các thuộc tính liên quan
 * từ {@link Environment}, sau đó hợp nhất với các giá trị deserializer mặc
 * định mà nền tảng yêu cầu.</p>
 *
 * @author Family Tree Platform Team
 */
@Configuration
public class KafkaConsumerConfig implements KafkaListenerConfigurer {

    /** Logger để ghi nhận các sự kiện consume. */
    private static final Logger LOG = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    /** Tên dịch vụ hiện tại, mặc định "unknown" nếu không cấu hình. */
    @Value("${spring.application.name:unknown}")
    private String serviceName;

    /** Môi trường Spring để đọc các thuộc tính cấu hình. */
    private final Environment environment;

    /**
     * Khởi tạo cấu hình với môi trường Spring.
     *
     * @param environment môi trường Spring
     */
    public KafkaConsumerConfig(Environment environment) {
        this.environment = environment;
    }

    /**
     * Tạo consumer factory mặc định với các deserializer phù hợp với nền tảng.
     *
     * <p>Sử dụng {@link ErrorHandlingDeserializer} bọc ngoài để chuyển lỗi
     * deserialize thành exception có thể retry thay vì crash consumer. Key
     * dùng {@link StringDeserializer}, value dùng {@link JsonDeserializer}
     * với các package đáng tin cậy được giới hạn trong {@code com.familya.*}.</p>
     *
     * @return {@link ConsumerFactory} đã cấu hình
     */
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> cfg = new HashMap<>();
        // Sao chép các thuộc tính spring.kafka.* nếu được cấu hình.
        copyIfPresent("spring.kafka.bootstrap-servers", ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, cfg);
        copyIfPresent("spring.kafka.consumer.group-id", ConsumerConfig.GROUP_ID_CONFIG, cfg);
        copyIfPresent("spring.kafka.consumer.auto-offset-reset", ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, cfg);
        copyIfPresent("spring.kafka.consumer.max-poll-records", ConsumerConfig.MAX_POLL_RECORDS_CONFIG, cfg);
        copyIfPresent("spring.kafka.consumer.key-deserializer", ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, cfg);
        copyIfPresent("spring.kafka.consumer.value-deserializer", ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, cfg);

        // Bọc deserializer bằng ErrorHandlingDeserializer để xử lý lỗi deserialize.
        cfg.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        cfg.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        cfg.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        cfg.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);

        // Giới hạn các package được deserialize tự động để chống deserialization gadget attack.
        cfg.put(JsonDeserializer.TRUSTED_PACKAGES, "com.familya.*,com.familya.platform.*");

        // Không dựa vào type info header — service chủ động ép kiểu sang Map hoặc DTO cụ thể.
        cfg.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        cfg.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "java.util.Map");

        return new DefaultKafkaConsumerFactory<>(cfg);
    }

    /**
     * Factory mặc định cho {@code @KafkaListener}, dùng chung cho phần lớn consumer.
     *
     * @param producerFactory nhà cung cấp ProducerFactory để gửi message tới DLQ
     *                       (sử dụng {@code ObjectProvider} để tránh phụ thuộc cứng)
     * @return {@link KafkaListenerContainerFactory} mặc định
     */
    @Bean
    public KafkaListenerContainerFactory<?> kafkaListenerContainerFactory(
            org.springframework.beans.factory.ObjectProvider<ProducerFactory<?, ?>> producerFactory) {
        return buildFactory(producerFactory);
    }

    /**
     * Factory chuyên dụng cho các listener lệnh Saga trong nền tảng.
     *
     * <p>Tái sử dụng consumer factory, concurrency và error handler mặc định.
     * Tên bean {@code "sagaCommandListenerContainerFactory"} được tham chiếu
     * trong annotation {@code @KafkaListener(containerFactory = "...")}.</p>
     *
     * @param producerFactory nhà cung cấp ProducerFactory để gửi message tới DLQ
     * @return {@link KafkaListenerContainerFactory} cho Saga command
     */
    @Bean("sagaCommandListenerContainerFactory")
    public KafkaListenerContainerFactory<?> sagaCommandListenerContainerFactory(
            org.springframework.beans.factory.ObjectProvider<ProducerFactory<?, ?>> producerFactory) {
        return buildFactory(producerFactory);
    }

    /**
     * Helper dựng {@link ConcurrentKafkaListenerContainerFactory} với cấu hình
     * chuẩn: consumer factory, concurrency 3, observation bật và error handler
     * kèm DLQ recoverer (nếu có producer factory).
     *
     * @param producerFactory nhà cung cấp ProducerFactory để gửi message tới DLQ
     * @return factory đã cấu hình
     */
    private ConcurrentKafkaListenerContainerFactory<String, Object> buildFactory(
            org.springframework.beans.factory.ObjectProvider<ProducerFactory<?, ?>> producerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        // Consumer factory dùng chung cho cả hai factory.
        factory.setConsumerFactory(consumerFactory());
        // Chạy 3 consumer thread song song cho mỗi partition để tăng throughput.
        factory.setConcurrency(3);
        // Bật observation để tích hợp với Micrometer/OpenTelemetry.
        factory.getContainerProperties().setObservationEnabled(true);

        // Nếu producer factory tồn tại, cấu hình DLQ recoverer + error handler.
        ProducerFactory<?, ?> pf = producerFactory.getIfAvailable();
        if (pf != null) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            ProducerFactory<Object, Object> typedPf = (ProducerFactory) pf;

            // Recoverer chuyển message lỗi sang <topic>.dlq với cùng partition.
            DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                    new KafkaTemplate<>(typedPf),
                    (record, ex) -> new TopicPartition(record.topic() + ".dlq", record.partition()));

            // Error handler: retry 3 lần với backoff 1000ms cố định, sau đó chuyển DLQ.
            DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
            factory.setCommonErrorHandler(errorHandler);
        }
        return factory;
    }

    /**
     * Hook cho phép cấu hình bổ sung khi đăng ký các KafkaListener.
     *
     * @param registrar đăng ký do Spring Kafka cung cấp
     */
    @Override
    public void configureKafkaListeners(KafkaListenerEndpointRegistrar registrar) {
        // Hiện tại không cần cấu hình bổ sung vì @KafkaListener đã tự quản lý
        // container factory. Phương thức này được giữ để có thể mở rộng sau.
    }

    /**
     * Wrapper idempotent mặc định cho listener. Consumer nên gọi phương thức
     * này từ {@code @KafkaListener} để đảm bảo dedupe theo {@code event_id}.
     *
     * @param record       bản ghi Kafka đến
     * @param consumerName tên consumer (không sử dụng trong triển khai hiện tại
     *                     nhưng giữ để tương thích với chữ ký mở rộng)
     * @param exists       hàm kiểm tra sự tồn tại của event_id (thường là
     *                     {@code inboxStore::exists})
     * @param <T>          kiểu giá trị của message
     * @return {@code true} nếu nên xử lý (chưa từng xử lý), {@code false} nếu
     *         bản ghi thiếu event_id hoặc đã được xử lý trước đó
     */
    public static <T> boolean shouldProcess(ConsumerRecord<String, T> record, String consumerName,
                                            java.util.function.Function<String, Boolean> exists) {
        // Bước 1: Đọc event_id từ header. Nếu thiếu, message không hợp lệ và cần bỏ qua.
        String eventId = headerString(record, "event_id");
        if (eventId == null) {
            LOG.warn("Dropping record without event_id topic={} partition={} offset={}",
                    record.topic(), record.partition(), record.offset());
            return false;
        }
        // Bước 2: Nếu inbox đã có event_id này thì bỏ qua (đã xử lý trước đó).
        return !exists.apply(eventId);
    }

    /**
     * Trích xuất giá trị header từ {@link ConsumerRecord} dưới dạng chuỗi UTF-8.
     *
     * @param record bản ghi Kafka
     * @param name   tên header cần đọc
     * @return giá trị header dạng chuỗi hoặc {@code null} nếu không có
     */
    private static String headerString(ConsumerRecord<?, ?> record, String name) {
        // Lấy header cuối cùng có tên name; nếu không có, trả về null.
        var h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value());
    }

    /**
     * Sao chép thuộc tính từ {@link Environment} sang Map cấu hình Kafka nếu giá trị tồn tại.
     *
     * @param propertyKey key trong {@link Environment} (vd {@code spring.kafka.bootstrap-servers})
     * @param kafkaKey    key trong cấu hình Kafka (vd {@link ConsumerConfig#BOOTSTRAP_SERVERS_CONFIG})
     * @param target      Map đích nhận giá trị
     */
    private void copyIfPresent(String propertyKey, String kafkaKey, Map<String, Object> target) {
        String value = environment.getProperty(propertyKey);
        // Chỉ sao chép khi giá trị khác null và không rỗng để tránh override giá trị mặc định.
        if (value != null && !value.isBlank()) {
            target.put(kafkaKey, value);
        }
    }
}
