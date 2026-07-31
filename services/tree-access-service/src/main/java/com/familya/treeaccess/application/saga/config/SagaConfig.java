package com.familya.treeaccess.application.saga.config;

import com.familya.treeaccess.application.saga.retry.RandomSagaJitterSource;
import com.familya.treeaccess.application.saga.retry.SagaClock;
import com.familya.treeaccess.application.saga.retry.SagaJitterSource;
import com.familya.treeaccess.application.saga.retry.SagaRetryPolicy;
import com.familya.treeaccess.application.saga.retry.SystemSagaClock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SagaProperties.class)
public class SagaConfig {

    @Bean
    public SagaClock sagaClock() {
        return new SystemSagaClock();
    }

    @Bean
    public SagaJitterSource sagaJitterSource() {
        return new RandomSagaJitterSource();
    }

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
