package com.familya.treeaccess.application.saga.config;

/**
 * Thuộc tính retry của Saga: backoff cơ sở, backoff tối đa và phần trăm jitter.
 * Lồng trong {@link SagaProperties} dưới khoá {@code retry}.
 */
public final class SagaRetryProperties {
    /** Backoff cơ sở giữa hai lần retry (millisecond), mặc định 1 giây. */
    private long baseBackoffMs = 1000;
    /** Backoff tối đa được phép (millisecond), mặc định 60 giây. */
    private long maxBackoffMs = 60000;
    /** Phần trăm jitter áp dụng lên backoff, mặc định 20%. */
    private int jitterPercent = 20;

    /**
     * @return backoff cơ sở (millisecond)
     */
    public long getBaseBackoffMs() { return baseBackoffMs; }
    /**
     * @param baseBackoffMs backoff cơ sở (millisecond)
     */
    public void setBaseBackoffMs(long baseBackoffMs) { this.baseBackoffMs = baseBackoffMs; }

    /**
     * @return backoff tối đa (millisecond)
     */
    public long getMaxBackoffMs() { return maxBackoffMs; }
    /**
     * @param maxBackoffMs backoff tối đa (millisecond)
     */
    public void setMaxBackoffMs(long maxBackoffMs) { this.maxBackoffMs = maxBackoffMs; }

    /**
     * @return phần trăm jitter
     */
    public int getJitterPercent() { return jitterPercent; }
    /**
     * @param jitterPercent phần trăm jitter
     */
    public void setJitterPercent(int jitterPercent) { this.jitterPercent = jitterPercent; }
}
