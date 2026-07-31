package com.familya.treeaccess.application.saga.retry;

import java.util.Objects;
import java.util.Random;
import java.util.function.DoubleSupplier;

/**
 * Cài đặt {@link SagaJitterSource} dựa trên {@link Random}. Cho phép tiêm
 * nguồn số ngẫu nhiên để hỗ trợ kiểm thử deterministic.
 */
public final class RandomSagaJitterSource implements SagaJitterSource {
    /** Nguồn cung cấp số thực trong [0, 1). */
    private final DoubleSupplier random;

    /**
     * Khởi tạo nguồn jitter dùng {@link Math#random()} mặc định.
     */
    public RandomSagaJitterSource() {
        this(Math::random);
    }

    /**
     * Khởi tạo nguồn jitter với seed cố định (dùng cho kiểm thử).
     *
     * @param seed hạt giống cho {@link Random}
     */
    public RandomSagaJitterSource(long seed) {
        this(new Random(seed)::nextDouble);
    }

    /**
     * Khởi tạo nguồn jitter với {@link DoubleSupplier} tuỳ ý.
     *
     * @param random nguồn cung cấp số thực trong [0, 1)
     * @throws NullPointerException nếu {@code random} là null
     */
    public RandomSagaJitterSource(DoubleSupplier random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    /**
     * Tính jitter nằm trong khoảng {@code [0, baseMs * percent / 100]}.
     *
     * @param baseMs  giá trị cơ sở (millisecond)
     * @param percent phần trăm áp dụng
     * @return số millisecond jitter
     * @throws IllegalArgumentException nếu {@code baseMs < 0} hoặc {@code percent} ngoài [0, 100]
     * @throws IllegalStateException    nếu delegate trả giá trị không hợp lệ
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
     * Tính {@code value * percent / 100} an toàn khi {@code value} lớn,
     * tránh tràn số.
     *
     * @param value   giá trị gốc
     * @param percent phần trăm
     * @return kết quả phép nhân phần trăm
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
