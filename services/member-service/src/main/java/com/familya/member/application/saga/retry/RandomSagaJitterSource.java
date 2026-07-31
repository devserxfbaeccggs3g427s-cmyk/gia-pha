package com.familya.member.application.saga.retry;

import java.util.Objects;
import java.util.Random;
import java.util.function.DoubleSupplier;

public final class RandomSagaJitterSource implements SagaJitterSource {
    private final DoubleSupplier random;

    public RandomSagaJitterSource() {
        this(Math::random);
    }

    public RandomSagaJitterSource(long seed) {
        this(new Random(seed)::nextDouble);
    }

    public RandomSagaJitterSource(DoubleSupplier random) {
        this.random = Objects.requireNonNull(random, "random");
    }

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

    private static long percentageOf(long value, int percent) {
        long quotient = value / 100;
        long remainder = value % 100;
        if (quotient > Long.MAX_VALUE / percent) {
            return Long.MAX_VALUE;
        }
        return quotient * percent + remainder * percent / 100;
    }
}
