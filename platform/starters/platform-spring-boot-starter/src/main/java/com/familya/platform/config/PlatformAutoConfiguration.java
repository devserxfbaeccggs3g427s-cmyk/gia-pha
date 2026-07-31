package com.familya.platform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

/**
 * Lớp cấu hình tự động (auto-configuration) cho các thành phần nền tảng chung.
 *
 * <p>Khi dịch vụ import starter {@code platform-spring-boot-starter}, Spring Boot
 * sẽ nạp lớp này thông qua cơ chế auto-configuration và đăng ký các bean dùng
 * chung sau:</p>
 * <ul>
 *   <li>{@link ObjectMapper} có hỗ trợ {@link JavaTimeModule} để (de)serialize
 *       các kiểu thời gian của Java 8+ (Instant, LocalDate, ...). Đây là bean
 *       chính (primary) để đảm bảo mọi serializer đều dùng chung cấu hình.</li>
 *   <li>{@link NamedParameterJdbcTemplate} dựa trên {@link DataSource} mặc định
 *       của ứng dụng, dùng cho các thao tác JDBC có tham số theo tên.</li>
 * </ul>
 *
 * <p>Lớp này cũng kích hoạt {@link PlatformProperties} thông qua
 * {@link EnableConfigurationProperties} để các thuộc tính dưới prefix
 * {@code familya.*} có thể được inject vào các bean khác.</p>
 *
 * @author Family Tree Platform Team
 */
@Configuration
@EnableConfigurationProperties(PlatformProperties.class)
public class PlatformAutoConfiguration {

    /**
     * Tạo bean {@link ObjectMapper} dùng chung cho toàn bộ ứng dụng.
     *
     * <p>{@link JavaTimeModule} được đăng ký để Jackson có thể xử lý các kiểu
     * {@code java.time.*}. Bean này được đánh dấu {@link Primary} để tránh xung
     * đột khi có nhiều cấu hình ObjectMapper trong context.</p>
     *
     * @return {@link ObjectMapper} đã được cấu hình
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        // Tạo ObjectMapper mới và đăng ký JavaTimeModule để xử lý Instant,
        // LocalDate, ZonedDateTime... theo chuẩn ISO-8601 thay vì timestamp.
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    /**
     * Tạo bean {@link NamedParameterJdbcTemplate} dựa trên {@link DataSource}
     * được Spring Boot tự động cấu hình.
     *
     * <p>Sử dụng NamedParameter giúp câu SQL dễ đọc và bảo trì hơn so với
     * việc truyền tham số theo vị trí, đặc biệt với các câu truy vấn phức tạp
     * có nhiều tham số.</p>
     *
     * @param ds {@link DataSource} do Spring Boot cung cấp
     * @return {@link NamedParameterJdbcTemplate} sẵn sàng sử dụng
     */
    @Bean
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource ds) {
        // Bọc DataSource trong NamedParameterJdbcTemplate; bean sẽ được các
        // thành phần khác của nền tảng (Outbox, Inbox, Idempotency, ...) inject.
        return new NamedParameterJdbcTemplate(ds);
    }
}
