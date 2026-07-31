package com.familya.treeaccess.application.saga.config;

public final class SagaDeadlineProperties {
    private long scanIntervalMs = 60000;
    private int batchSize = 100;
    private long stepTimeoutMs = 30000;

    public long getScanIntervalMs() { return scanIntervalMs; }
    public void setScanIntervalMs(long scanIntervalMs) { this.scanIntervalMs = scanIntervalMs; }

    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }

    public long getStepTimeoutMs() { return stepTimeoutMs; }
    public void setStepTimeoutMs(long stepTimeoutMs) { this.stepTimeoutMs = stepTimeoutMs; }
}
