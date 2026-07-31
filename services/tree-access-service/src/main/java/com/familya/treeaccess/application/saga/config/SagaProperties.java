package com.familya.treeaccess.application.saga.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Thuộc tính gốc cho Saga tree-access. Ánh xạ các khoá YAML có tiền tố
 * {@code familya.treeauth.saga}.
 */
@ConfigurationProperties(prefix = "familya.treeauth.saga")
public class SagaProperties {
    /** Có bật deadline scanner hay không (mặc định {@code true}). */
    private boolean schedulerEnabled = true;
    /** Cấu hình deadline — được lồng vào {@link SagaDeadlineProperties}. */
    private SagaDeadlineProperties deadline = new SagaDeadlineProperties();
    /** Cấu hình retry — được lồng vào {@link SagaRetryProperties}. */
    private SagaRetryProperties retry = new SagaRetryProperties();

    /**
     * @return {@code true} nếu deadline scanner được bật
     */
    public boolean isSchedulerEnabled() { return schedulerEnabled; }
    /**
     * @param schedulerEnabled bật/tắt deadline scanner
     */
    public void setSchedulerEnabled(boolean schedulerEnabled) { this.schedulerEnabled = schedulerEnabled; }

    /**
     * @return cấu hình deadline hiện tại
     */
    public SagaDeadlineProperties getDeadline() { return deadline; }
    /**
     * @param deadline cấu hình deadline mới
     */
    public void setDeadline(SagaDeadlineProperties deadline) { this.deadline = deadline; }

    /**
     * @return cấu hình retry hiện tại
     */
    public SagaRetryProperties getRetry() { return retry; }
    /**
     * @param retry cấu hình retry mới
     */
    public void setRetry(SagaRetryProperties retry) { this.retry = retry; }
}
