package com.familya.treeaccess.application.saga.retry;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class SagaRetryPolicy {
    private final long baseBackoffMs;
    private final long maxBackoffMs;
    private final int jitterPercent;
    private final int maxAttemptsSnapshot;
    private final SagaJitterSource jitterSource;

    public SagaRetryPolicy(long baseBackoffMs,
                           long maxBackoffMs,
                           int jitterPercent,
                           int maxAttemptsSnapshot,
                           SagaJitterSource jitterSource) {
        if (baseBackoffMs <= 0) {
            throw new IllegalArgumentException("baseBackoffMs must be positive");
        }
        if (maxBackoffMs < baseBackoffMs) {
            throw new IllegalArgumentException("maxBackoffMs must be at least baseBackoffMs");
        }
        if (jitterPercent < 0 || jitterPercent > 100) {
            throw new IllegalArgumentException("jitterPercent must be between 0 and 100");
        }
        if (maxAttemptsSnapshot < 1) {
            throw new IllegalArgumentException("maxAttemptsSnapshot must be positive");
        }
        this.baseBackoffMs = baseBackoffMs;
        this.maxBackoffMs = maxBackoffMs;
        this.jitterPercent = jitterPercent;
        this.maxAttemptsSnapshot = maxAttemptsSnapshot;
        this.jitterSource = Objects.requireNonNull(jitterSource, "jitterSource");
    }

    public long baseBackoffMs() {
        return baseBackoffMs;
    }

    public long maxBackoffMs() {
        return maxBackoffMs;
    }

    public int jitterPercent() {
        return jitterPercent;
    }

    public int maxAttemptsSnapshot() {
        return maxAttemptsSnapshot;
    }

    public Duration nextDelay(int attemptCount) {
        if (attemptCount < 1) {
            throw new IllegalArgumentException("attemptCount must be positive");
        }
        long cappedBackoff = cappedBackoff(attemptCount);
        long jitter = jitterSource.boundedJitter(cappedBackoff, jitterPercent);
        if (jitter < 0) {
            throw new IllegalStateException("jitter must not be negative");
        }
        long delay = jitter > Long.MAX_VALUE - cappedBackoff
                ? Long.MAX_VALUE
                : cappedBackoff + jitter;
        return Duration.ofMillis(delay);
    }

    public Instant nextAttemptAt(Instant now, int attemptCount, Instant operationDeadline) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(operationDeadline, "operationDeadline");
        Instant latest = minusOneMillisecond(operationDeadline);
        Instant candidate;
        try {
            candidate = now.plus(nextDelay(attemptCount));
        } catch (DateTimeException | ArithmeticException exception) {
            candidate = Instant.MAX;
        }
        return candidate.isAfter(latest) ? latest : candidate;
    }

    private long cappedBackoff(int attemptCount) {
        long backoff = baseBackoffMs;
        for (int attempt = 1; attempt < attemptCount && backoff < maxBackoffMs; attempt++) {
            if (backoff > maxBackoffMs / 2) {
                return maxBackoffMs;
            }
            if (backoff > Long.MAX_VALUE / 2) {
                return maxBackoffMs;
            }
            backoff *= 2;
        }
        return Math.min(backoff, maxBackoffMs);
    }

    private static Instant minusOneMillisecond(Instant instant) {
        try {
            return instant.minusMillis(1);
        } catch (DateTimeException | ArithmeticException exception) {
            return Instant.MIN;
        }
    }
}
