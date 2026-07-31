package com.familya.member.application.saga.config;

/**
 * Cấu hình cho chính sách retry (exponential backoff với jitter) của Saga. Liên kết với
 * prefix {@code familia.member.saga.retry}.
 */
public final class SagaRetryProperties {
    /** Thời gian chờ ban đầu giữa các lần retry (ms). Mặc định 1000ms. */
    private long baseBackoffMs = 1000;
    /** Thời gian chờ tối đa giữa các lần retry (ms). Mặc định 60000ms. */
    private long maxBackoffMs = 60000;
    /** Phần trăm jitter để tránh hiện tượng thundering herd. Mặc định 20%. */
    private int jitterPercent = 20;

    /** Lấy thời gian backoff ban đầu (ms). */
    public long getBaseBackoffMs() { return baseBackoffMs; }
    /** Đặt thời gian backoff ban đầu (ms). */
    public void setBaseBackoffMs(long baseBackoffMs) { this.baseBackoffMs = baseBackoffMs; }

    /** Lấy thời gian backoff tối đa (ms). */
    public long getMaxBackoffMs() { return maxBackoffMs; }
    /** Đặt thời gian backoff tối đa (ms). */
    public void setMaxBackoffMs(long maxBackoffMs) { this.maxBackoffMs = maxBackoffMs; }

    /** Lấy phần trăm jitter. */
    public int getJitterPercent() { return jitterPercent; }
    /** Đặt phần trăm jitter. */
    public void setJitterPercent(int jitterPercent) { this.jitterPercent = jitterPercent; }
}
