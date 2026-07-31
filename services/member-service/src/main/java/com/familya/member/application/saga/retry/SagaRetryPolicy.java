package com.familya.member.application.saga.retry;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Chính sách retry cho Saga sử dụng exponential backoff kết hợp jitter. Tính toán
 * khoảng thời gian chờ giữa các lần thử lại với giới hạn trên {@link #maxBackoffMs}.
 */
public final class SagaRetryPolicy {
    private final long baseBackoffMs;
    private final long maxBackoffMs;
    private final int jitterPercent;
    private final int maxAttemptsSnapshot;
    private final SagaJitterSource jitterSource;

    /**
     * Khởi tạo chính sách retry.
     *
     * @param baseBackoffMs       backoff ban đầu (ms), phải dương
     * @param maxBackoffMs        backoff tối đa (ms), phải &ge; {@code baseBackoffMs}
     * @param jitterPercent       phần trăm jitter (0-100)
     * @param maxAttemptsSnapshot số lần thử tối đa cho snapshot (giá trị tham chiếu)
     * @param jitterSource        nguồn jitter (không null)
     * @throws IllegalArgumentException nếu bất kỳ tham số nào không hợp lệ
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

    /** Lấy backoff ban đầu (ms). */
    public long baseBackoffMs() {
        return baseBackoffMs;
    }

    /** Lấy backoff tối đa (ms). */
    public long maxBackoffMs() {
        return maxBackoffMs;
    }

    /** Lấy phần trăm jitter. */
    public int jitterPercent() {
        return jitterPercent;
    }

    /** Lấy số lần thử tối đa. */
    public int maxAttemptsSnapshot() {
        return maxAttemptsSnapshot;
    }

    /**
     * Tính khoảng thời gian chờ cho lần thử tiếp theo. Áp dụng exponential backoff
     * (gấp đôi qua mỗi lần thử, có giới hạn trên) cộng thêm jitter ngẫu nhiên.
     *
     * @param attemptCount số lần đã thử (bắt đầu từ 1)
     * @return {@link Duration} chờ trước lần thử tiếp theo
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
     * Tính thời điểm thử tiếp theo, đảm bảo không vượt quá deadline của operation.
     *
     * @param now                thời điểm hiện tại
     * @param attemptCount       số lần đã thử
     * @param operationDeadline  deadline của toàn bộ operation
     * @return {@link Instant} thời điểm thử tiếp theo
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
     * Tính backoff đã được giới hạn trên cho lần thử {@code attemptCount}.
     *
     * @param attemptCount số lần thử
     * @return backoff (ms) không vượt quá {@link #maxBackoffMs}
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
     * Trừ 1 millisecond, xử lý tràn số an toàn.
     *
     * @param instant thời điểm đầu vào
     * @return thời điểm sau khi trừ 1ms, hoặc {@link Instant#MIN} nếu tràn
     */
    private static Instant minusOneMillisecond(Instant instant) {
        try {
            return instant.minusMillis(1);
        } catch (DateTimeException | ArithmeticException exception) {
            return Instant.MIN;
        }
    }
}
