package com.familya.treeaccess.application.saga.config;

import com.familya.treeaccess.application.saga.retry.RandomSagaJitterSource;
import com.familya.treeaccess.application.saga.retry.SagaClock;
import com.familya.treeaccess.application.saga.retry.SagaJitterSource;
import com.familya.treeaccess.application.saga.retry.SagaRetryPolicy;
import com.familya.treeaccess.application.saga.retry.SystemSagaClock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình các bean phụ trợ cho Saga của tree-access-service. Các bean bao gồm:
 *
 * <ul>
 *   <li>{@link SagaClock} — đồng hồ dùng cho các thao tác deadline.</li>
 *   <li>{@link SagaJitterSource} — nguồn số ngẫu nhiên cho jitter.</li>
 *   <li>{@link SagaRetryPolicy} — chính sách retry với backoff mũ và jitter.</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(SagaProperties.class)
public class SagaConfig {

    /**
     * Khởi tạo {@link SagaClock} dựa trên {@link SystemSagaClock} (sử dụng {@link java.time.Instant#now()}).
     *
     * @return bean {@link SagaClock}
     */
    @Bean
    public SagaClock sagaClock() {
        return new SystemSagaClock();
    }

    /**
     * Khởi tạo {@link SagaJitterSource} ngẫu nhiên dựa trên {@link RandomSagaJitterSource}.
     *
     * @return bean {@link SagaJitterSource}
     */
    @Bean
    public SagaJitterSource sagaJitterSource() {
        return new RandomSagaJitterSource();
    }

    /**
     * Khởi tạo {@link SagaRetryPolicy} từ các thuộc tính trong {@link SagaProperties}.
     * Hiện tại số lần thử tối đa mặc định được gán cứng là {@code 5}.
     *
     * @param props thuộc tính cấu hình Saga
     * @return bean {@link SagaRetryPolicy}
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
