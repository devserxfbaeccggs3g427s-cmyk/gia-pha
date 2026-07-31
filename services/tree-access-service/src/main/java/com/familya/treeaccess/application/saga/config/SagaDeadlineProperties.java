package com.familya.treeaccess.application.saga.config;

/**
 * Thuộc tính cấu hình cho deadline scanner của Saga. Có thể nạp từ file
 * {@code application.yml} dưới khoá {@code familya.treeauth.saga.deadline}.
 */
public final class SagaDeadlineProperties {
    /** Khoảng thời gian giữa hai lần quét deadline, mặc định 60 giây. */
    private long scanIntervalMs = 60000;
    /** Số Saga/bước xử lý tối đa mỗi lượt quét, mặc định 100. */
    private int batchSize = 100;
    /** Thời gian chờ tối đa cho một bước trước khi tính timeout, mặc định 30 giây. */
    private long stepTimeoutMs = 30000;

    /**
     * @return khoảng thời gian giữa hai lần quét deadline (millisecond)
     */
    public long getScanIntervalMs() { return scanIntervalMs; }
    /**
     * @param scanIntervalMs khoảng thời gian giữa hai lần quét deadline (millisecond)
     */
    public void setScanIntervalMs(long scanIntervalMs) { this.scanIntervalMs = scanIntervalMs; }

    /**
     * @return kích thước batch xử lý mỗi lượt quét
     */
    public int getBatchSize() { return batchSize; }
    /**
     * @param batchSize kích thước batch
     */
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }

    /**
     * @return thời gian chờ của một bước trước khi timeout (millisecond)
     */
    public long getStepTimeoutMs() { return stepTimeoutMs; }
    /**
     * @param stepTimeoutMs thời gian chờ của một bước (millisecond)
     */
    public void setStepTimeoutMs(long stepTimeoutMs) { this.stepTimeoutMs = stepTimeoutMs; }
}
