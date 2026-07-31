package com.familya.treeaccess.application.saga.retry;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Chính sách retry cho Saga với backoff mũ và jitter. Đảm bảo thời điểm
 * retry tiếp theo không bao giờ vượt quá {@code operationDeadline}.
 */
public final class SagaRetryPolicy {
    /** Backoff cơ sở (millisecond). */
    private final long baseBackoffMs;
    /** Backoff tối đa (millisecond). */
    private final long maxBackoffMs;
    /** Phần trăm jitter áp lên backoff. */
    private final int jitterPercent;
    /** Số lần thử tối đa đã chụp tại thời điểm tạo policy. */
    private final int maxAttemptsSnapshot;
    /** Nguồn jitter. */
    private final SagaJitterSource jitterSource;

    /**
     * Khởi tạo policy.
     *
     * @param baseBackoffMs       backoff cơ sở (phải &gt; 0)
     * @param maxBackoffMs        backoff tối đa (phải &ge; baseBackoffMs)
     * @param jitterPercent       phần trăm jitter (0-100)
     * @param maxAttemptsSnapshot số lần thử tối đa (snapshot)
     * @param jitterSource        nguồn jitter
     * @throws IllegalArgumentException nếu tham số không hợp lệ
     * @throws NullPointerException     nếu {@code jitterSource} là null
     */
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

    /**
     * @return backoff cơ sở (millisecond)
     */
    public long baseBackoffMs() {
        return baseBackoffMs;
    }

    /**
     * @return backoff tối đa (millisecond)
     */
    public long maxBackoffMs() {
        return maxBackoffMs;
    }

    /**
     * @return phần trăm jitter
     */
    public int jitterPercent() {
        return jitterPercent;
    }

    /**
     * @return số lần thử tối đa đã chụp khi tạo policy
     */
    public int maxAttemptsSnapshot() {
        return maxAttemptsSnapshot;
    }

    /**
     * Tính độ trễ cho lần retry tiếp theo dựa trên số lần thử và cộng jitter.
     *
     * @param attemptCount số lần thử hiện tại (phải &ge; 1)
     * @return {@link Duration} thời gian chờ trước khi retry
     * @throws IllegalArgumentException nếu {@code attemptCount < 1}
     */
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

    /**
     * Tính thời điểm retry tiếp theo có giới hạn bởi deadline thao tác.
     *
     * @param now               thời điểm hiện tại
     * @param attemptCount      số lần thử hiện tại
     * @param operationDeadline deadline tuyệt đối của thao tác
     * @return thời điểm retry đề xuất (luôn &le; deadline - 1ms)
     */
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

    /**
     * Tính backoff theo cấp số nhân có giới hạn trên bởi {@link #maxBackoffMs}.
     *
     * @param attemptCount số lần thử
     * @return backoff (millisecond) đã được giới hạn
     */
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

    /**
     * Trừ 1 millisecond nhưng xử lý an toàn nếu tràn/ngoại lệ ngày-tháng.
     *
     * @param instant {@link Instant} đầu vào
     * @return {@link Instant} đã trừ 1 ms hoặc {@link Instant#MIN}
     */
    private static Instant minusOneMillisecond(Instant instant) {
        try {
            return instant.minusMillis(1);
        } catch (DateTimeException | ArithmeticException exception) {
            return Instant.MIN;
        }
    }
}
