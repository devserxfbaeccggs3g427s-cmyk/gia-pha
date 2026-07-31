package com.familya.member.application.saga.config;

import com.familya.member.application.saga.retry.RandomSagaJitterSource;
import com.familya.member.application.saga.retry.SagaClock;
import com.familya.member.application.saga.retry.SagaJitterSource;
import com.familya.member.application.saga.retry.SagaRetryPolicy;
import com.familya.member.application.saga.retry.SystemSagaClock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình Spring cho các bean phụ trợ của Saga xóa thành viên: {@link SagaClock},
 * {@link SagaJitterSource} và {@link SagaRetryPolicy}. Lớp này thuộc tầng
 * application/saga/config và được kích hoạt bởi {@link EnableConfigurationProperties}.
 */
@Configuration
@EnableConfigurationProperties(SagaProperties.class)
public class SagaConfig {

    /**
     * Bean {@link SagaClock} mặc định — dùng đồng hồ hệ thống.
     * @return {@link SystemSagaClock} trả về {@link Instant#now()}
     */
    @Bean
    public SagaClock sagaClock() {
        return new SystemSagaClock();
    }

    /**
     * Bean {@link SagaJitterSource} mặc định — dùng nguồn ngẫu nhiên.
     * @return {@link RandomSagaJitterSource} tạo giá trị trong [0, 1)
     */
    @Bean
    public SagaJitterSource sagaJitterSource() {
        return new RandomSagaJitterSource();
    }

    /**
     * Bean {@link SagaRetryPolicy} được cấu hình từ {@link SagaProperties}.
     *
     * @param props cấu hình {@code familia.member.saga}
     * @return chính sách retry sử dụng exponential backoff với jitter
     */
    @Bean
    public SagaRetryPolicy sagaRetryPolicy(SagaProperties props) {
        SagaRetryProperties retry = props.getRetry();
        return new SagaRetryPolicy(
                retry.getBaseBackoffMs(),
                retry.getMaxBackoffMs(),
                retry.getJitterPercent(),
                5,
                sagaJitterSource());
    }
}
