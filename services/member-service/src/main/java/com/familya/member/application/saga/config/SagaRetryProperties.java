package com.familya.member.application.saga.config;

public final class SagaRetryProperties {
    private long baseBackoffMs = 1000;
    private long maxBackoffMs = 60000;
    private int jitterPercent = 20;

    public long getBaseBackoffMs() { return baseBackoffMs; }
    public void setBaseBackoffMs(long baseBackoffMs) { this.baseBackoffMs = baseBackoffMs; }

    public long getMaxBackoffMs() { return maxBackoffMs; }
    public void setMaxBackoffMs(long maxBackoffMs) { this.maxBackoffMs = maxBackoffMs; }

    public int getJitterPercent() { return jitterPercent; }
    public void setJitterPercent(int jitterPercent) { this.jitterPercent = jitterPercent; }
}
