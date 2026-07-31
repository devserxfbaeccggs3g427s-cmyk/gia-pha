package com.familya.member.application.saga.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Lớp cấu hình gốc cho Saga của Member Service. Liên kết với prefix {@code familia.member.saga}
 * trong {@code application.yml} hoặc biến môi trường.
 */
@ConfigurationProperties(prefix = "familya.member.saga")
public class SagaProperties {
    /** Có bật deadline scanner hay không. Mặc định {@code true}. */
    private boolean schedulerEnabled = true;
    /** Cấu hình deadline scanner. */
    private SagaDeadlineProperties deadline = new SagaDeadlineProperties();
    /** Cấu hình retry/backoff. */
    private SagaRetryProperties retry = new SagaRetryProperties();

    /** Kiểm tra deadline scanner có được bật hay không. */
    public boolean isSchedulerEnabled() { return schedulerEnabled; }
    /** Bật/tắt deadline scanner. */
    public void setSchedulerEnabled(boolean schedulerEnabled) { this.schedulerEnabled = schedulerEnabled; }

    /** Lấy cấu hình deadline. */
    public SagaDeadlineProperties getDeadline() { return deadline; }
    /** Đặt cấu hình deadline. */
    public void setDeadline(SagaDeadlineProperties deadline) { this.deadline = deadline; }

    /** Lấy cấu hình retry. */
    public SagaRetryProperties getRetry() { return retry; }
    /** Đặt cấu hình retry. */
    public void setRetry(SagaRetryProperties retry) { this.retry = retry; }
}
