package com.familya.member.application.saga.config;

/**
 * Cấu hình cho deadline scanner của Saga: chu kỳ quét, kích thước batch và thời gian
 * timeout mặc định cho mỗi bước. Liên kết với prefix {@code familia.member.saga.deadline}.
 */
public final class SagaDeadlineProperties {
    /** Chu kỳ quét của deadline scanner (ms). Mặc định 60s. */
    private long scanIntervalMs = 60000;
    /** Số lượng bản ghi tối đa xử lý trong một lượt quét. Mặc định 100. */
    private int batchSize = 100;
    /** Thời gian chờ tối đa cho mỗi bước Saga trước khi timeout (ms). Mặc định 30s. */
    private long stepTimeoutMs = 30000;

    /** Lấy chu kỳ quét (ms). */
    public long getScanIntervalMs() { return scanIntervalMs; }
    /** Đặt chu kỳ quét (ms). */
    public void setScanIntervalMs(long scanIntervalMs) { this.scanIntervalMs = scanIntervalMs; }

    /** Lấy kích thước batch. */
    public int getBatchSize() { return batchSize; }
    /** Đặt kích thước batch. */
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }

    /** Lấy thời gian timeout của mỗi bước (ms). */
    public long getStepTimeoutMs() { return stepTimeoutMs; }
    /** Đặt thời gian timeout của mỗi bước (ms). */
    public void setStepTimeoutMs(long stepTimeoutMs) { this.stepTimeoutMs = stepTimeoutMs; }
}
