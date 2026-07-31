package com.familya.member.application.saga.retry;

import java.util.Objects;
import java.util.Random;
import java.util.function.DoubleSupplier;

/**
 * Triển khai {@link SagaJitterSource} dùng {@link Random} hoặc {@link DoubleSupplier}
 * tùy ý để tạo jitter ngẫu nhiên trong một biên độ xác định. Tránh được hiện tượng
 * thundering herd khi nhiều Saga cùng retry sau cùng một sự cố.
 */
public final class RandomSagaJitterSource implements SagaJitterSource {
    private final DoubleSupplier random;

    /** Khởi tạo với {@link Math#random()} làm nguồn ngẫu nhiên. */
    public RandomSagaJitterSource() {
        this(Math::random);
    }

    /**
     * Khởi tạo với seed cố định — dùng cho test để có kết quả lặp lại.
     *
     * @param seed giá trị seed cho {@link Random}
     */
    public RandomSagaJitterSource(long seed) {
        this(new Random(seed)::nextDouble);
    }

    /**
     * Khởi tạo với một {@link DoubleSupplier} tùy ý.
     *
     * @param random hàm trả về giá trị [0, 1)
     */
    public RandomSagaJitterSource(DoubleSupplier random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    /**
     * Sinh jitter có giới hạn: trả về số nguyên ngẫu nhiên trong [0, bound].
     *
     * @param baseMs  giá trị cơ sở (ms)
     * @param percent phần trăm của {@code baseMs} làm cận trên
     * @return giá trị jitter (ms)
     * @throws IllegalArgumentException nếu {@code baseMs < 0} hoặc {@code percent} ngoài [0, 100]
     * @throws IllegalStateException    nếu delegate không trả về giá trị hữu hạn trong [0, 1)
     */
    @Override
    public long boundedJitter(long baseMs, int percent) {
        if (baseMs < 0) {
            throw new IllegalArgumentException("baseMs must not be negative");
        }
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("percent must be between 0 and 100");
        }
        if (baseMs == 0 || percent == 0) {
            return 0;
        }
        long bound = percentageOf(baseMs, percent);
        if (bound == 0) {
            return 0;
        }
        double value = random.getAsDouble();
        if (value < 0.0 || value >= 1.0 || !Double.isFinite(value)) {
            throw new IllegalStateException("random delegate must return a finite value in [0, 1)");
        }
        return (long) (value * (bound + 1.0));
    }

    /**
     * Tính {@code percent}% của {@code value} một cách an toàn với long.
     *
     * @param value   giá trị cơ sở
     * @param percent phần trăm
     * @return kết quả của {@code value * percent / 100}
     */
    private static long percentageOf(long value, int percent) {
        long quotient = value / 100;
        long remainder = value % 100;
        if (quotient > Long.MAX_VALUE / percent) {
            return Long.MAX_VALUE;
        }
        return quotient * percent + remainder * percent / 100;
    }
}
