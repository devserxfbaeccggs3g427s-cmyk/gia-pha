package com.familya.treeaccess.application.saga.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "familya.treeauth.saga")
public class SagaProperties {
    private boolean schedulerEnabled = true;
    private SagaDeadlineProperties deadline = new SagaDeadlineProperties();
    private SagaRetryProperties retry = new SagaRetryProperties();

    public boolean isSchedulerEnabled() { return schedulerEnabled; }
    public void setSchedulerEnabled(boolean schedulerEnabled) { this.schedulerEnabled = schedulerEnabled; }

    public SagaDeadlineProperties getDeadline() { return deadline; }
    public void setDeadline(SagaDeadlineProperties deadline) { this.deadline = deadline; }

    public SagaRetryProperties getRetry() { return retry; }
    public void setRetry(SagaRetryProperties retry) { this.retry = retry; }
}
