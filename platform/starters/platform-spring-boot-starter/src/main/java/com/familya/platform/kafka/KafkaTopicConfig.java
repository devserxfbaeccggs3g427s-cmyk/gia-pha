package com.familya.platform.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Khai báo các Kafka topic cho nền tảng.
 *
 * <p>Các dịch vụ bổ sung các topic mà chúng publish hoặc subscribe thông qua
 * các bean {@link NewTopic} tương ứng. Hệ số replication và số partition là
 * các giá trị baseline có thể bị override theo từng môi trường.</p>
 *
 * <p><b>Các giá trị cấu hình nền tảng:</b></p>
 * <ul>
 *   <li>{@code familya.kafka.partitions} (mặc định 12) — số partition.</li>
 *   <li>{@code familya.kafka.replication-factor} (mặc định 3) — hệ số replication.</li>
 * </ul>
 *
 * @author Family Tree Platform Team
 */
@Configuration
public class KafkaTopicConfig {

    /** Số partition mặc định cho các topic khai báo tại đây. */
    @Value("${familya.kafka.partitions:12}")
    private int partitions;

    /** Hệ số replication mặc định cho các topic khai báo tại đây. */
    @Value("${familya.kafka.replication-factor:3}")
    private short replicationFactor;

    /**
     * Khai báo topic {@code identity.events.v1} — topic chính chứa các sự kiện
     * liên quan đến danh tính (identity) trong nền tảng.
     *
     * <p>Cấu hình đặc biệt:</p>
     * <ul>
     *   <li>{@code retention.ms = 220752000000} (~7 năm) — tuân thủ yêu cầu
     *       lưu trữ dài hạn cho dữ liệu danh tính.</li>
     *   <li>{@code min.insync.replicas = 2} — đảm bảo dữ liệu được ghi đồng
     *       bộ lên ít nhất 2 replica trước khi ack.</li>
     * </ul>
     *
     * @return bean {@link NewTopic} mô tả topic identity.events.v1
     */
    @Bean
    public NewTopic identityEvents() {
        return TopicBuilder.name("identity.events.v1")
                .partitions(partitions)
                .replicas(replicationFactor)
                .config("retention.ms", "220752000000") // 7 năm
                .config("min.insync.replicas", "2")
                .build();
    }

    /**
     * Khai báo topic DLQ {@code identity.events.v1.dlq} — nhận các bản ghi
     * bị lỗi khi xử lý và không thể retry thành công từ {@code identity.events.v1}.
     *
     * <p>Cấu hình đặc biệt:</p>
     * <ul>
     *   <li>Số partition giảm còn 6 để tiết kiệm tài nguyên cho DLQ.</li>
     *   <li>{@code retention.ms = 1209600000} (14 ngày) — đủ để đội vận hành
     *       xử lý thủ công các bản ghi lỗi.</li>
     * </ul>
     *
     * @return bean {@link NewTopic} mô tả topic DLQ
     */
    @Bean
    public NewTopic identityEventsDlq() {
        return TopicBuilder.name("identity.events.v1.dlq")
                .partitions(6)
                .replicas(replicationFactor)
                .config("retention.ms", "1209600000")  // 14 ngày
                .build();
    }
}
